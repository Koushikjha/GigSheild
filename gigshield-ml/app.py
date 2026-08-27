#!/usr/bin/env python3
"""
GigShield ML sidecar — FastAPI serving layer.

Before this file existed, gigshield-ml was three standalone training
scripts (risk_model.py, fraud_model.py, prediction_model.py) with no HTTP
server at all, even though the Spring Boot backend's MlServiceClient has
always expected one at three fixed paths:

    GET  /risk-score?city=&latitude=&longitude=&platform=
    POST /fraud-check   {userId, city, latitude, longitude, eventType, policyId}
    GET  /trigger-check?city=

That mismatch meant every ML-dependent feature (premium pricing, fraud
scoring, and the entire automatic RAIN/AQI trigger → claim → payout
pipeline) was permanently unreachable — every call from Java fell straight
through to MlServiceClient's hard-coded fallbacks. This module is the
missing piece: it wires the trained models (when present) and a resilient
rule-based fallback (when not) into exactly the contract Java already
expects, so the three-service system actually functions end to end.

Run directly for local dev:
    uvicorn app:app --host 0.0.0.0 --port 5000 --reload

See Dockerfile / entrypoint.sh for the containerized path, which trains
models on first boot if none are cached in the `models/` volume.
"""

from __future__ import annotations

import logging
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from inference.fraud import score_fraud
from inference.risk import score_risk
from inference.triggers import check_triggers
from inference.weather import get_weather_signal, get_weather_signal_at
from inference.cities import lookup_city

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
logger = logging.getLogger("gigshield.ml")

app = FastAPI(
    title="GigShield ML Sidecar",
    description="Risk scoring, fraud detection, and parametric trigger checks for GigShield.",
    version="1.0.0",
)

FRAUD_AUTO_APPROVE_THRESHOLD = 40  # mirrors AppConstants.FRAUD_AUTO_APPROVE_THRESHOLD


# ── Response / request models (field names match the Java DTOs exactly —
#    Jackson on the Spring side uses default camelCase, no naming strategy) ──

class RiskScoreResponse(BaseModel):
    riskScore: float = Field(..., description="0-100")
    recommendedPremium: int = Field(..., description="₹15-30")
    riskBand: str = Field(..., description="LOW | MEDIUM | HIGH")


class FraudCheckRequest(BaseModel):
    userId: Optional[int] = None
    city: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    eventType: Optional[str] = None
    policyId: Optional[int] = None


class FraudCheckResponse(BaseModel):
    fraudScore: int = Field(..., description="0-100")
    recommendation: str = Field(..., description="AUTO_APPROVE | REVIEW")


class TriggerCheckResponse(BaseModel):
    city: str
    activeTriggers: list[str]
    metricValues: dict[str, float]


@app.get("/health")
def health():
    """Liveness/readiness probe for docker-compose / k8s."""
    return {"status": "UP", "service": "gigshield-ml"}


@app.get("/risk-score", response_model=RiskScoreResponse)
def risk_score(
    city: str = Query(...),
    latitude: float = Query(...),
    longitude: float = Query(...),
    platform: Optional[str] = Query(None),
    at: Optional[str] = Query(None, description="ISO-8601 timestamp — score as of this moment instead of live/now"),
):
    """
    ``at``, when given, scores the place as of that timestamp (via Open-
    Meteo's recent-history window) rather than live weather — used to
    corroborate a specific past event (an order cancellation, a disruption
    Kafka message) against what conditions actually were at the time, not
    whatever they happen to be when the request is processed. Omitting
    ``at`` keeps the original "live now" behavior (e.g. the worker-facing
    risk snapshot / policy pricing, which is deliberately always current).
    """
    parsed_at = _parse_at(at)
    signal = get_weather_signal_at(latitude, longitude, parsed_at) if parsed_at else get_weather_signal(latitude, longitude)
    result = score_risk(signal.rainfall_mm, signal.aqi, signal.temperature_c, signal.humidity_pct)
    logger.info(
        "risk-score city=%s platform=%s at=%s -> score=%.1f premium=%d band=%s (live_weather=%s)",
        city, platform, at, result["riskScore"], result["recommendedPremium"], result["riskBand"], signal.is_live,
    )
    return result


def _parse_at(at: Optional[str]) -> Optional[datetime]:
    if not at:
        return None
    try:
        return datetime.fromisoformat(at)
    except ValueError:
        logger.warning("Could not parse at=%r as ISO-8601 — treating as live/now instead", at)
        return None


@app.post("/fraud-check", response_model=FraudCheckResponse)
def fraud_check(request: FraudCheckRequest):
    score = score_fraud(request.city, request.latitude, request.longitude)
    recommendation = "AUTO_APPROVE" if score < FRAUD_AUTO_APPROVE_THRESHOLD else "REVIEW"
    logger.info(
        "fraud-check userId=%s city=%s eventType=%s -> score=%d rec=%s",
        request.userId, request.city, request.eventType, score, recommendation,
    )
    return {"fraudScore": score, "recommendation": recommendation}


@app.get("/trigger-check", response_model=TriggerCheckResponse)
def trigger_check(
    city: str = Query(...),
    latitude: Optional[float] = Query(None, description="Real coordinates to corroborate against, instead of the city centroid"),
    longitude: Optional[float] = Query(None),
    at: Optional[str] = Query(None, description="ISO-8601 timestamp — corroborate as of this moment instead of live/now"),
):
    if latitude is None and longitude is None and lookup_city(city) is None:
        # Unknown city and no coordinates given isn't an error — Java's
        # EventTriggerScheduler polls a fixed, configurable city list; an
        # unrecognized one just never fires a trigger, matching
        # MlServiceClient's own "empty response" fallback semantics.
        logger.warning("trigger-check requested for unmonitored city=%s with no coordinates", city)
    return check_triggers(city, latitude, longitude, _parse_at(at))
