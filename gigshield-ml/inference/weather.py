"""
Live weather + air-quality signal via Open-Meteo (https://open-meteo.com).

Open-Meteo's forecast and air-quality APIs are free and require no API key,
which is why they were picked over the openweathermap/waqi.info integrations
that were sketched-but-commented-out in application.yaml (those need paid
keys). Every call is wrapped so that a network failure, timeout, or bad
response degrades to a deterministic fallback instead of raising — mirroring
the fail-safe philosophy already used on the Java side (MlServiceClient never
lets an ML outage block the insurance pipeline; this module extends the same
guarantee one hop further, to the external weather provider).
"""

from __future__ import annotations

import logging
import time
from dataclasses import dataclass
from datetime import datetime, timedelta
from hashlib import sha256

import requests

logger = logging.getLogger("gigshield.ml.weather")

FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
AIR_QUALITY_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
REQUEST_TIMEOUT_SECONDS = 4.0

# Open-Meteo's forecast + air-quality endpoints both serve recent history
# directly (via start_date/end_date) with no multi-day reanalysis delay —
# unlike the separate archive-api.open-meteo.com, which lags ~5 days. 90
# days (under their 92-day cap, with a 2-day safety margin) is plenty for
# GigShield: order-cancellation claims must be reported within
# AppConstants.ORDER_CANCELLED_REPORT_WINDOW_HOURS (72h) on the Java side
# anyway, so this ceiling is never actually the binding constraint.
_HISTORICAL_MAX_AGE_DAYS = 90
_FUTURE_TOLERANCE_MINUTES = 5

_CACHE_TTL_SECONDS = 300.0
_cache: dict[str, tuple[float, "WeatherSignal"]] = {}

# Historical (as-of a past timestamp) lookups are cached without expiry —
# the weather at 2pm last Tuesday for a given coordinate is a fixed fact,
# not something that goes stale like a "live" reading does.
_historical_cache: dict[str, "WeatherSignal"] = {}


@dataclass
class WeatherSignal:
    rainfall_mm: float       # precipitation, mm (current hour) — same unit as training data
    aqi: float                # Open-Meteo US AQI (0-500), same scale as training's clipped proxy
    temperature_c: float
    humidity_pct: float
    is_live: bool             # False when this is the offline fallback, not a real reading


def _cache_key(lat: float, lon: float) -> str:
    # Round to ~1km so nearby coordinates share a cache entry.
    return f"{round(lat, 2)}:{round(lon, 2)}"


def _fallback_signal(lat: float, lon: float) -> WeatherSignal:
    """
    Deterministic, non-random offline substitute so the sidecar keeps
    responding (with mild, plausible values) even with zero network access —
    e.g. an air-gapped dev sandbox. Seeded from the coordinates so the same
    location always yields the same fallback rather than flapping between
    calls.
    """
    seed = int(sha256(f"{round(lat, 2)}:{round(lon, 2)}".encode()).hexdigest(), 16)
    rainfall_mm = (seed % 40) / 2.0          # 0–20 mm
    aqi = 60 + (seed // 40) % 180            # 60–240
    temperature_c = 22 + (seed // 1000) % 14  # 22–36 °C
    humidity_pct = 40 + (seed // 100) % 45    # 40–85 %
    return WeatherSignal(rainfall_mm, float(aqi), float(temperature_c), float(humidity_pct), is_live=False)


def get_weather_signal(lat: float, lon: float) -> WeatherSignal:
    """Fetch current precipitation/AQI/temperature/humidity for a coordinate."""
    key = _cache_key(lat, lon)
    cached = _cache.get(key)
    if cached and (time.monotonic() - cached[0]) < _CACHE_TTL_SECONDS:
        return cached[1]

    try:
        signal = _fetch_live(lat, lon)
    except Exception as exc:  # noqa: BLE001 - any network/parsing failure degrades gracefully
        logger.warning("Live weather fetch failed for (%.4f, %.4f): %s — using offline fallback", lat, lon, exc)
        signal = _fallback_signal(lat, lon)

    _cache[key] = (time.monotonic(), signal)
    return signal


def get_weather_signal_at(lat: float, lon: float, at: datetime) -> WeatherSignal:
    """
    Fetch precipitation/AQI/temperature/humidity AS OF a specific past (or
    present) timestamp — not "right now".

    This exists because timing matters for corroborating a disruption: a
    worker reporting a cancelled order files the claim after the fact, and
    an automated Kafka message can sit briefly before a consumer picks it
    up. Scoring either against "current" weather would corroborate (or
    reject) the claim against conditions at the wrong moment — e.g. it
    stopped raining by the time the claim was processed, even though it was
    genuinely pouring at the reported cancellation time. Open-Meteo's
    forecast/air-quality endpoints serve recent history via start_date/
    end_date with no extra delay, so this reuses the same two APIs
    `get_weather_signal` does, just anchored to `at`'s date/hour instead of
    "now".
    """
    now = datetime.now()
    key = f"{round(lat, 2)}:{round(lon, 2)}:{at.strftime('%Y-%m-%dT%H')}"

    cached = _historical_cache.get(key)
    if cached is not None:
        return cached

    if at > now + timedelta(minutes=_FUTURE_TOLERANCE_MINUTES):
        logger.warning("get_weather_signal_at called with a future timestamp (%s > now=%s) — using fallback", at, now)
        signal = _fallback_signal(lat, lon)
    elif (now - at).days > _HISTORICAL_MAX_AGE_DAYS:
        logger.warning(
            "get_weather_signal_at called for %s, %d+ days old (max %d) — Open-Meteo's rolling "
            "history window has passed, using fallback",
            at, (now - at).days, _HISTORICAL_MAX_AGE_DAYS,
        )
        signal = _fallback_signal(lat, lon)
    else:
        try:
            signal = _fetch_historical(lat, lon, at)
        except Exception as exc:  # noqa: BLE001 - any network/parsing failure degrades gracefully
            logger.warning("Historical weather fetch failed for (%.4f, %.4f) at=%s: %s — using offline fallback",
                            lat, lon, at, exc)
            signal = _fallback_signal(lat, lon)

    _historical_cache[key] = signal
    return signal


def _closest_hour_index(time_labels: list[str], at: datetime) -> int:
    """Open-Meteo's hourly `time` arrays are ISO strings like '2026-08-24T14:00' —
    pick whichever entry's hour is nearest to `at`."""
    target = at.replace(minute=0, second=0, microsecond=0)
    best_idx, best_diff = 0, None
    for idx, label in enumerate(time_labels):
        candidate = datetime.fromisoformat(label)
        diff = abs((candidate - target).total_seconds())
        if best_diff is None or diff < best_diff:
            best_idx, best_diff = idx, diff
    return best_idx


def _fetch_historical(lat: float, lon: float, at: datetime) -> WeatherSignal:
    date_str = at.strftime("%Y-%m-%d")

    weather_resp = requests.get(
        FORECAST_URL,
        params={
            "latitude": lat,
            "longitude": lon,
            "start_date": date_str,
            "end_date": date_str,
            "hourly": "precipitation,temperature_2m,relative_humidity_2m",
            "timezone": "auto",
        },
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    weather_resp.raise_for_status()
    hourly = weather_resp.json()["hourly"]
    idx = _closest_hour_index(hourly["time"], at)

    aqi_resp = requests.get(
        AIR_QUALITY_URL,
        params={
            "latitude": lat,
            "longitude": lon,
            "start_date": date_str,
            "end_date": date_str,
            "hourly": "us_aqi,pm2_5",
            "timezone": "auto",
        },
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    aqi_resp.raise_for_status()
    aqi_hourly = aqi_resp.json()["hourly"]
    aqi_idx = _closest_hour_index(aqi_hourly["time"], at) if aqi_hourly.get("time") else idx

    aqi_value = aqi_hourly.get("us_aqi", [None])[aqi_idx] if aqi_hourly.get("us_aqi") else None
    if aqi_value is None:
        pm25 = aqi_hourly.get("pm2_5", [])
        aqi_value = pm25[aqi_idx] if aqi_idx < len(pm25) else 0.0

    return WeatherSignal(
        rainfall_mm=float(hourly.get("precipitation", [0.0])[idx] or 0.0),
        aqi=float(max(0.0, min(500.0, aqi_value or 0.0))),
        temperature_c=float(hourly.get("temperature_2m", [25.0])[idx] or 25.0),
        humidity_pct=float(hourly.get("relative_humidity_2m", [55.0])[idx] or 55.0),
        is_live=True,
    )


def _fetch_live(lat: float, lon: float) -> WeatherSignal:
    weather_resp = requests.get(
        FORECAST_URL,
        params={
            "latitude": lat,
            "longitude": lon,
            "current": "precipitation,temperature_2m,relative_humidity_2m",
            "timezone": "auto",
        },
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    weather_resp.raise_for_status()
    current = weather_resp.json()["current"]

    aqi_resp = requests.get(
        AIR_QUALITY_URL,
        params={
            "latitude": lat,
            "longitude": lon,
            "current": "us_aqi,pm2_5,pm10",
            "timezone": "auto",
        },
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    aqi_resp.raise_for_status()
    aqi_current = aqi_resp.json()["current"]

    aqi_value = aqi_current.get("us_aqi")
    if aqi_value is None:
        # Fall back to raw PM2.5 concentration as an AQI-like proxy, matching
        # the training pipeline's own "worst pollutant concentration" proxy.
        aqi_value = aqi_current.get("pm2_5", 0.0)

    return WeatherSignal(
        rainfall_mm=float(current.get("precipitation") or 0.0),
        aqi=float(max(0.0, min(500.0, aqi_value))),
        temperature_c=float(current.get("temperature_2m") or 25.0),
        humidity_pct=float(current.get("relative_humidity_2m") or 55.0),
        is_live=True,
    )
