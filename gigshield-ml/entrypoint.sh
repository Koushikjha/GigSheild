#!/usr/bin/env sh
# GigShield ML sidecar entrypoint.
#
# The service is usable the instant it starts — app.py's inference layer
# falls back to rule-based heuristics for any model that isn't on disk yet.
# So instead of blocking startup on training (which can take a few minutes
# against the full aqi.csv), we kick training off in the background and
# start serving immediately; each inference module re-checks disk until it
# finds its model, so the sidecar quietly upgrades from heuristics to
# trained-model scoring once training finishes — no restart needed.
#
# Set SKIP_TRAINING=true to disable this entirely (e.g. CI, or a volume that
# already has trained models baked in).
set -e

MODEL_DIR="$(dirname "$0")/models"

if [ "${SKIP_TRAINING}" != "true" ]; then
  if [ -f "${MODEL_DIR}/risk_model.pkl" ] && [ -f "${MODEL_DIR}/fraud_model.pkl" ] && [ -f "${MODEL_DIR}/predictive_risk_model.pkl" ]; then
    echo "[entrypoint] All models already present in ${MODEL_DIR} — skipping training."
  else
    echo "[entrypoint] Training models in the background (serving starts immediately with heuristic fallback)..."
    python train_all.py > /tmp/train_all.log 2>&1 &
  fi
else
  echo "[entrypoint] SKIP_TRAINING=true — serving with whatever is in ${MODEL_DIR} (heuristic fallback if empty)."
fi

echo "[entrypoint] Starting GigShield ML sidecar on port ${ML_PORT:-5000}"
exec uvicorn app:app --host 0.0.0.0 --port "${ML_PORT:-5000}"
