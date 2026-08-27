"""
Serving layer for GET /risk-score.

Originally intended as a wrapper around risk_model.py's trained
RandomForestRegressor, rescaling its 0.0-1.0 training-target output to the
0-100 RiskScoreResponse contract. That model is NOT used here — see the
"Known limitation" docstring on score_risk() below for why: its `rainfall`
feature was trained on monthly cumulative totals (data/weather.csv), which
is a completely different scale from the hourly/point-in-time reading this
module is actually called with, and using it silently under-scores real
disruptive weather no matter how severe. score_risk() always uses the
rule-based heuristic instead, which was calibrated directly against the
real serving-time input scale. This also means /risk-score never depends
on risk_model.pkl existing at all — it degrades in quality only if the ML
sidecar's own process is down, not based on whether a model file happens
to be present.
"""

from __future__ import annotations

PREMIUM_MIN_INR = 15
PREMIUM_MAX_INR = 30

# Same low/medium/high cut points the product already uses on the frontend
# risk-score mock, kept consistent here so live scoring doesn't change the
# worker-facing meaning of "your zone is medium risk".
RISK_BAND_LOW_MAX = 35.0
RISK_BAND_MEDIUM_MAX = 65.0

# Heuristic normalization bounds — calibrated against the real serving-time
# input scale (a single hourly/point-in-time weather reading), unlike
# risk_model.pkl's training scale (see module docstring). Deliberately
# generous so real rainfall/AQI readings don't all pin at the ceiling.
_HEURISTIC_RAINFALL_CEILING_MM = 60.0
_HEURISTIC_AQI_CEILING = 400.0


def _risk_band(score_0_100: float) -> str:
    if score_0_100 < RISK_BAND_LOW_MAX:
        return "LOW"
    if score_0_100 < RISK_BAND_MEDIUM_MAX:
        return "MEDIUM"
    return "HIGH"


def _heuristic_score(rainfall_mm: float, aqi: float) -> float:
    """Same 0.5/0.3/0.2 rainfall/AQI/historical weighting as training,
    minus the historical term (no live disruption history at inference time
    without a DB lookup), renormalized over the remaining two terms."""
    rain_component = min(1.0, rainfall_mm / _HEURISTIC_RAINFALL_CEILING_MM)
    aqi_component = min(1.0, aqi / _HEURISTIC_AQI_CEILING)
    return 100.0 * (0.625 * rain_component + 0.375 * aqi_component)


def score_risk(rainfall_mm: float, aqi: float, temperature_c: float, humidity_pct: float) -> dict:
    """Returns {riskScore, recommendedPremium, riskBand} ready to serialize.

    Deliberately does NOT call risk_model.predict_risk(), even when
    risk_model.pkl is present on disk. That model's `rainfall` feature was
    trained on data/weather.csv's MONTHLY cumulative rainfall totals per
    IMD subdivision (median ~42mm, 90th percentile ~337mm, max ~2363mm
    across the training set), but this function is always called with a
    single hourly/point-in-time precipitation reading — 0-20mm from the
    deterministic offline fallback, and realistically well under 100mm
    even for a genuine severe downpour live from Open-Meteo. A serving-time
    value that small normalizes to only ~1-3% on the scale the trained
    model actually learned "high rainfall" to mean, so the model silently
    predicts near-zero rainfall risk almost regardless of how severe
    conditions genuinely are right now — it never raises an exception, so
    a naive "try the model, fall back to the heuristic on error" approach
    would never catch this at all, and the result would be systematically
    under-priced premiums for genuinely disruptive weather (confirmed: a
    live 60mm/hour reading — an extreme downpour — normalizes to ~2.5% of
    the model's training-fit rainfall scale). The heuristic below has no
    such mismatch: its ceilings
    (_HEURISTIC_RAINFALL_CEILING_MM=60, _HEURISTIC_AQI_CEILING=400) were
    calibrated directly against this serving-time input scale, so it's the
    correct choice here even though a trained model sounds fancier.
    Retraining risk_model.pkl on point-in-time rainfall would fix this
    properly, but no hourly-resolution historical rainfall data is
    available in this dataset (weather.csv only has monthly totals) — see
    README "Known limitations".
    """
    score_0_100 = _heuristic_score(rainfall_mm, aqi)

    premium = round(PREMIUM_MIN_INR + (PREMIUM_MAX_INR - PREMIUM_MIN_INR) * (score_0_100 / 100.0))
    premium = max(PREMIUM_MIN_INR, min(PREMIUM_MAX_INR, premium))

    return {
        "riskScore": round(score_0_100, 2),
        "recommendedPremium": premium,
        "riskBand": _risk_band(score_0_100),
    }
