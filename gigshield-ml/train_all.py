#!/usr/bin/env python3
"""
Train all three GigShield models and write them to models/*.pkl.

    python train_all.py            # skip any model already on disk
    python train_all.py --force    # retrain everything

Training on the full aqi.csv (~435k rows) takes a few minutes on a modest
container; this is normally run once (baked into a volume or the image) —
see Dockerfile / entrypoint.sh, which call this automatically on first boot
if models/ is empty, and app.py's inference layer, which falls back to
rule-based heuristics if a model simply isn't there yet.
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import fraud_model
import risk_model
import prediction_model

MODELS = [
    ("risk_model (rainfall + AQI -> risk score)", risk_model),
    ("fraud_model (synthetic account features -> fraud probability)", fraud_model),
    ("prediction_model (rainfall + AQI -> disruption likely)", prediction_model),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--force", action="store_true", help="retrain even if a model file already exists")
    args = parser.parse_args()

    for label, module in MODELS:
        model_path: Path = module.MODEL_PATH
        if model_path.is_file() and not args.force:
            print(f"[skip] {label}: {model_path} already exists (use --force to retrain)")
            continue

        print(f"[train] {label} ...")
        started = time.monotonic()
        try:
            module.main()
        except FileNotFoundError as exc:
            print(f"[error] {label}: {exc}", file=sys.stderr)
            print("        (data/ CSVs missing — see gigshield-ml/README.md)", file=sys.stderr)
            continue
        elapsed = time.monotonic() - started
        print(f"[done]  {label} in {elapsed:.1f}s -> {model_path}")


if __name__ == "__main__":
    main()
