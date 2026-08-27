# GigShield ML sidecar

Serves the three endpoints the Spring Boot backend's `MlServiceClient`
expects:

| Method | Path             | Used by                                    |
|--------|------------------|---------------------------------------------|
| GET    | `/risk-score`    | `RiskService` — weekly premium pricing       |
| POST   | `/fraud-check`   | `FraudService` — claim auto-approve/review   |
| GET    | `/trigger-check` | `EventTriggerScheduler` — RAIN/AQI triggers → DisruptionEvent → Kafka → auto claims/payouts |
| GET    | `/health`        | container healthcheck                        |

## Run locally

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# optional but recommended — trains all 3 models into models/*.pkl
# (a few minutes; needs data/aqi.csv + data/weather.csv, already included)
python train_all.py

uvicorn app:app --reload --port 5000
```

The service works immediately even without training: every inference path
(`inference/risk.py`, `inference/fraud.py`, `inference/triggers.py`) falls
back to a transparent rule-based heuristic when its model file is missing,
so `docker compose up` never blocks on a multi-minute training run — see
`entrypoint.sh`, which trains in the background and serving quietly
upgrades from heuristic to trained-model scoring once training finishes.

## How scoring works

- **`/risk-score`** pulls live rainfall + AQI for the given coordinates from
  Open-Meteo (free, no API key) and runs them through `risk_model.py`'s
  trained `RandomForestRegressor`, rescaled from its training-target 0-1
  range to the 0-100 `riskScore` / ₹15-30 `recommendedPremium` the backend
  expects.
- **`/fraud-check`** loads `fraud_model.py`'s trained classifier directly and
  reads `predict_proba` (a graded 0-100 score) instead of the training
  script's binary `predict()` — the backend thresholds on a score
  (`FRAUD_AUTO_APPROVE_THRESHOLD = 40`), not a yes/no. Because the current
  `FraudCheckRequest` only carries `{userId, city, latitude, longitude,
  eventType, policyId}` — no account/device history — `gps_distance` is
  derived from the worker's coordinates vs. their city's centroid (a real
  signal) and the remaining four model features (`account_age`,
  `device_count`, `zone_change`, `claims_count`, `purchase_before_event`)
  use neutral "no evidence of anomaly" defaults. **Enhancement:** have the
  backend enrich `FraudCheckRequest` with real account-history features
  (from `User`/`Claim` records) so the model sees what it was actually
  trained to look for.
- **`/trigger-check`** fetches live rainfall/AQI for the requested city
  (city → centroid table in `inference/cities.py`) and fires `RAIN`/`AQI`
  against the thresholds from the product spec (rain-mm: 35.0, aqi: 300 —
  restored from the commented-out `gigshield.thresholds` block in
  `application.yaml`), optionally corroborated by `prediction_model.py`'s
  trained disruption classifier when available. `CURFEW`/`TRAFFIC`/`WAR`
  are intentionally never returned here — those stay admin-created via
  `POST /api/v1/events`.

## Training scripts

`risk_model.py`, `fraud_model.py`, `prediction_model.py` are unchanged
standalone trainers — each can still be run directly (`python
risk_model.py`) and prints metrics/feature importances. `train_all.py` is a
thin convenience wrapper that runs all three and skips any model already on
disk (`--force` to retrain).

## Docker

```bash
docker build -t gigshield-ml .
docker run -p 5000:5000 -v gigshield-ml-models:/app/models gigshield-ml
```

Mounting `/app/models` as a named volume means training only has to happen
once — subsequent container restarts start serving from the trained models
immediately. See the repo-root `docker-compose.yml` for the full
multi-service wiring (this service is `ml-sidecar`).
