"""
Serving wrapper around fraud_model.py's trained RandomForestClassifier.

Two corrections relative to the training script, needed to satisfy the
/fraud-check contract (FraudCheckResponse.fraudScore is a 0-100 *score*):

  1. fraud_model.predict() only exposes the final binary decision (0/1) at a
     hard-coded threshold. A binary in/out doesn't give the Spring Boot side
     anything to threshold against (AppConstants.FRAUD_AUTO_APPROVE_THRESHOLD
     = 40 on a 0-100 scale). This module loads the pickled classifier
     directly and uses predict_proba(...)[1] * 100 instead — the same model,
     read as a graded score rather than a coin flip.

  2. fraud_model's FEATURE_COLUMNS (gps_distance, account_age, device_count,
     zone_change, claims_count, purchase_before_event) describe account
     history the Java FraudCheckRequest does not currently carry (userId,
     city, latitude, longitude, eventType, policyId only — no device/claim
     history). gps_distance is derived here from the worker's submitted
     coordinates vs. their claimed city's centroid, which is a genuine
     signal. The remaining four features fall back to neutral "no evidence
     of anomaly" defaults and are clearly flagged as such — see the
     GigShield README's "Further enhancements" section for the proper fix
     (have the backend enrich FraudCheckRequest with real account-history
     features before calling the sidecar).
"""

from __future__ import annotations

import logging
import math
from pathlib import Path

import joblib

import fraud_model
from inference.cities import lookup_city

logger = logging.getLogger("gigshield.ml.fraud")

_model_cache = None

# Neutral defaults for account-history features not yet available to the
# sidecar (see module docstring, point 2).
DEFAULT_ACCOUNT_AGE_DAYS = 200
DEFAULT_DEVICE_COUNT = 1
DEFAULT_ZONE_CHANGE = 0
DEFAULT_CLAIMS_COUNT = 0
DEFAULT_PURCHASE_BEFORE_EVENT = 0

EARTH_RADIUS_KM = 6371.0


def _haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = math.sin(d_lat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(d_lon / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(min(1.0, math.sqrt(a)))


def _load_classifier():
    """
    Cache a successfully loaded model forever (it's immutable once trained),
    but keep re-checking disk on every call while it's missing — so a
    background training run (see entrypoint.sh) is picked up on its next
    completed request without requiring a server restart.
    """
    global _model_cache
    if _model_cache is not None:
        return _model_cache
    path = Path(fraud_model.MODEL_PATH)
    if not path.is_file():
        return None
    try:
        _model_cache = joblib.load(path)
        logger.info("Loaded fraud_model.pkl")
    except Exception as exc:  # noqa: BLE001
        logger.warning("Failed to load fraud_model.pkl (%s) — using heuristic", exc)
        _model_cache = None
    return _model_cache


def _gps_distance_km(city: str | None, latitude: float | None, longitude: float | None) -> float:
    centroid = lookup_city(city)
    if centroid is None or latitude is None or longitude is None:
        return 0.0
    return _haversine_km(centroid[0], centroid[1], latitude, longitude)


def _heuristic_score(gps_distance_km: float) -> float:
    """Rule-of-thumb fallback: the same >30km signal the training data's
    probabilistic labeler weights most heavily (0.3 of 1.0), scaled to 0-100
    and capped well below AUTO-REJECT so an unavailable model never triggers
    a hard denial on its own."""
    if gps_distance_km > 30:
        return 55.0
    if gps_distance_km > 10:
        return 25.0
    return 10.0


def score_fraud(city: str | None, latitude: float | None, longitude: float | None) -> int:
    """Returns an integer fraud score in [0, 100]."""
    gps_distance_km = _gps_distance_km(city, latitude, longitude)

    clf = _load_classifier()
    if clf is None:
        return round(_heuristic_score(gps_distance_km))

    try:
        import pandas as pd

        row = pd.DataFrame(
            [[
                gps_distance_km,
                DEFAULT_ACCOUNT_AGE_DAYS,
                DEFAULT_DEVICE_COUNT,
                DEFAULT_ZONE_CHANGE,
                DEFAULT_CLAIMS_COUNT,
                DEFAULT_PURCHASE_BEFORE_EVENT,
            ]],
            columns=fraud_model.FEATURE_COLUMNS,
        )
        fraud_probability = clf.predict_proba(row)[0, 1]
        return round(max(0.0, min(1.0, float(fraud_probability))) * 100)
    except Exception as exc:  # noqa: BLE001
        logger.warning("Fraud model prediction failed (%s) — using heuristic", exc)
        return round(_heuristic_score(gps_distance_km))
