"""
Serving wrapper for GET /trigger-check — decides which parametric triggers
(RAIN, AQI) are currently active for a monitored city.

This is the ML sidecar's half of the parametric-insurance loop: Spring
Boot's EventTriggerScheduler polls this endpoint every
`gigshield.scheduler.poll-interval-ms` (default 15 min) per monitored city,
and any trigger returned here becomes a DisruptionEvent — which, via Kafka,
fans out into automatic claims and payouts. Getting this endpoint wrong (or
leaving it unimplemented, as before) meant the entire automated-payout
pipeline could never fire on its own.

CURFEW / TRAFFIC / WAR are intentionally never returned here — those aren't
derivable from weather signals and stay admin-created
(POST /api/v1/events, sourceSystem=ADMIN), matching the product's existing
design (see EventController).

Thresholds mirror the commented-out `gigshield.thresholds` block in
application.yaml (rain-mm: 35.0, aqi: 300) — restoring the intended product
config rather than inventing new numbers.

Two callers use this differently:
  * EventTriggerScheduler polls by city name only (no specific worker to
    borrow coordinates from) — it still resolves a city centroid via
    `lookup_city`, live, exactly as before.
  * DisruptionEventVerificationService (Java) now calls this to corroborate
    a *specific* event, and passes real registered coordinates — the
    affected workers' own — plus the event's actual occurred-at time, so it
    checks the trigger against ``lat``/``lon``/``at`` explicitly instead of
    falling back to the city table.
"""

from __future__ import annotations

import logging
from datetime import datetime

from inference.cities import lookup_city
from inference.weather import get_weather_signal, get_weather_signal_at

logger = logging.getLogger("gigshield.ml.triggers")

RAIN_THRESHOLD_MM = 35.0
AQI_THRESHOLD = 300.0

# Optional extra corroboration from the trained predictive-disruption model,
# used only to demote a borderline metric-based trigger to "not yet" (it
# never invents a trigger the raw metrics didn't already suggest).
_predict_disruption = None


def _load_disruption_predictor():
    """Re-checks disk while unavailable so a model trained after startup
    (see entrypoint.sh's background training) is picked up automatically."""
    global _predict_disruption
    if _predict_disruption is not None:
        return _predict_disruption
    try:
        import prediction_model

        if prediction_model.MODEL_PATH.is_file():
            _predict_disruption = prediction_model.predict_disruption
    except Exception as exc:  # noqa: BLE001
        logger.info("Predictive disruption model unavailable (%s) — using raw thresholds only", exc)
    return _predict_disruption


def check_triggers(
    city: str,
    lat: float | None = None,
    lon: float | None = None,
    at: datetime | None = None,
) -> dict:
    """
    If ``lat``/``lon`` are given, they're used directly (real coordinates —
    a specific worker's, or an average of the affected workers'), which is
    always more precise than the city-centroid fallback below. ``at``, if
    given, corroborates the trigger as of that timestamp rather than "now".
    """
    if lat is None or lon is None:
        centroid = lookup_city(city)
        if centroid is None:
            logger.warning("No known centroid for city=%s and no coordinates given — trigger-check returns no active triggers", city)
            return {"city": city, "activeTriggers": [], "metricValues": {}}
        lat, lon = centroid

    signal = get_weather_signal_at(lat, lon, at) if at is not None else get_weather_signal(lat, lon)

    active_triggers: list[str] = []
    metric_values: dict[str, float] = {}

    rain_candidate = signal.rainfall_mm >= RAIN_THRESHOLD_MM
    aqi_candidate = signal.aqi >= AQI_THRESHOLD

    if rain_candidate or aqi_candidate:
        predictor = _load_disruption_predictor()
        if predictor is not None:
            try:
                corroborated = bool(predictor(
                    signal.rainfall_mm, signal.aqi, signal.temperature_c, signal.humidity_pct, 0,
                ))
            except Exception as exc:  # noqa: BLE001
                logger.warning("Disruption model prediction failed (%s) — trusting raw thresholds", exc)
                corroborated = True
        else:
            corroborated = True
    else:
        corroborated = False

    if rain_candidate and corroborated:
        active_triggers.append("RAIN")
        metric_values["RAIN"] = round(signal.rainfall_mm, 2)

    if aqi_candidate and corroborated:
        active_triggers.append("AQI")
        metric_values["AQI"] = round(signal.aqi, 2)

    logger.info(
        "trigger-check city=%s rainfall=%.1fmm aqi=%.0f live=%s -> %s",
        city, signal.rainfall_mm, signal.aqi, signal.is_live, active_triggers,
    )

    return {"city": city, "activeTriggers": active_triggers, "metricValues": metric_values}
