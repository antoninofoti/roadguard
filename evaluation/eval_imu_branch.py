"""
eval_imu_branch.py
==================
Evaluation script for the IMU-only branch of the RoadGuard late-fusion pipeline.

Dataset: nickkotarelas/road-quality-dataset (Thessaloniki)
  - CSV format: [timestamp, acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z, label]
  - label=1  → pothole / road anomaly
  - label=0  → normal road surface

Windowing:
  - window_size = 100 samples @ 100 Hz  → 1-second windows
  - overlap     = 50%                   → step = 50 samples

Scoring:
  - magnitude = sqrt(acc_x² + acc_y² + acc_z²)
  - imu_score = 1 if max(magnitude in window) > threshold else 0
  - Threshold sweep over [9.0, 10.0, 11.0, 12.0, 15.0] m/s²

Output:
  - Console: Precision, Recall, F1, Accuracy (best threshold)
  - File:    evaluation/results/imu_branch_metrics.json
"""

import os
import json
import math
import argparse
import numpy as np
import pandas as pd
from sklearn.metrics import precision_score, recall_score, f1_score, accuracy_score

# ─── Constants ────────────────────────────────────────────────────────────────
SAMPLE_RATE   = 100      # Hz
WINDOW_SIZE   = 100      # samples  (1 second)
STEP_SIZE     = 50       # 50% overlap
THRESHOLDS    = [9.0, 10.0, 11.0, 12.0, 15.0]   # m/s²
GRAVITY       = 9.81     # used only for display; raw acc values assumed in m/s²

RESULTS_DIR   = os.path.join(os.path.dirname(__file__), "results")
OUTPUT_FILE   = os.path.join(RESULTS_DIR, "imu_branch_metrics.json")

DATA_PATHS = [
    "/content/data/thessaloniki/imu_normalized.csv",
    "data/thessaloniki/imu_data.csv",
    "data/thessaloniki/road_quality_data.csv",
    "data/thessaloniki/data.csv",
    "data/thessaloniki/thessaloniki_imu.csv",
]

# ─── Helpers ──────────────────────────────────────────────────────────────────

def find_dataset(paths: list[str]) -> str | None:
    """Return the first existing path from the candidate list."""
    for p in paths:
        if os.path.exists(p):
            return p
    return None


def load_imu_data(csv_path: str) -> pd.DataFrame:
    """
    Load IMU data from CSV.

    Accepts both the canonical Thessaloniki column names and the simplified
    format used in the RoadGuard synthetic reference dataset.

    Expected columns (case-insensitive):
        timestamp, acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z, label
    """
    df = pd.read_csv(csv_path)
    df.columns = [c.strip().lower() for c in df.columns]

    # Column aliasing — some Kaggle exports use different names
    rename_map = {
        "accelerometer_x": "acc_x",  "accel_x": "acc_x",  "ax": "acc_x",
        "accelerometer_y": "acc_y",  "accel_y": "acc_y",  "ay": "acc_y",
        "accelerometer_z": "acc_z",  "accel_z": "acc_z",  "az": "acc_z",
        "gyroscope_x":     "gyro_x", "gx":      "gyro_x",
        "gyroscope_y":     "gyro_y", "gy":      "gyro_y",
        "gyroscope_z":     "gyro_z", "gz":      "gyro_z",
        "class":           "label",  "target":  "label",  "anomaly": "label",
    }
    df.rename(columns=rename_map, inplace=True)

    required = {"acc_x", "acc_y", "acc_z", "label"}
    missing  = required - set(df.columns)
    if missing:
        raise ValueError(f"CSV is missing required columns: {missing}\nFound: {list(df.columns)}")

    # Ensure numeric
    for col in ["acc_x", "acc_y", "acc_z", "label"]:
        df[col] = pd.to_numeric(df[col], errors="coerce")
    df.dropna(subset=["acc_x", "acc_y", "acc_z", "label"], inplace=True)
    df["label"] = df["label"].astype(int)

    return df.reset_index(drop=True)


def segment_windows(df: pd.DataFrame) -> list[tuple[pd.DataFrame, int]]:
    """
    Slice DataFrame into overlapping windows.

    Returns a list of (window_df, majority_label) tuples.
    Majority vote is used to assign a single label to each window.
    """
    windows = []
    n = len(df)
    for start in range(0, n - WINDOW_SIZE + 1, STEP_SIZE):
        end    = start + WINDOW_SIZE
        window = df.iloc[start:end]
        # Majority vote for label
        label  = int(window["label"].value_counts().idxmax())
        windows.append((window, label))
    return windows


def compute_imu_score(window: pd.DataFrame, threshold: float) -> int:
    """
    Compute binary anomaly score for a single IMU window.

    magnitude = sqrt(acc_x² + acc_y² + acc_z²)
    score     = 1 if max(magnitude) > threshold else 0
    """
    mag = np.sqrt(
        window["acc_x"] ** 2 +
        window["acc_y"] ** 2 +
        window["acc_z"] ** 2
    )
    return 1 if mag.max() > threshold else 0


def generate_synthetic_dataset(n_samples: int = 10000) -> pd.DataFrame:
    """
    Generate a synthetic IMU dataset for CI / demo purposes when the real
    Thessaloniki dataset is not available.

    Data is generated in contiguous blocks so that windows (100 samples) reliably
    contain either all-normal or all-pothole readings.

    Distribution:
      - 70% normal road  (label=0):  acc_z ~9.81 m/s²   (gravity only)
      - 30% pothole      (label=1):  acc_z ~13.0-16.0 m/s² (sharp impact)
    """
    rng = np.random.default_rng(seed=42)

    n_normal  = int(n_samples * 0.7)
    n_pothole = n_samples - n_normal

    # ── Normal blocks ────────────────────────────────────────────────────────
    acc_normal       = rng.normal(0, 0.2, (n_normal, 3))
    acc_normal[:, 2] += 9.81          # steady gravity baseline

    gyro_normal = rng.normal(0, 0.03, (n_normal, 3))

    df_normal = pd.DataFrame({
        "acc_x":  acc_normal[:, 0], "acc_y": acc_normal[:, 1], "acc_z": acc_normal[:, 2],
        "gyro_x": gyro_normal[:, 0], "gyro_y": gyro_normal[:, 1], "gyro_z": gyro_normal[:, 2],
        "label":  0,
    })

    # ── Pothole blocks ───────────────────────────────────────────────────────
    # Each pothole window has a sharp impact spike in acc_z (13–17 m/s²)
    acc_pothole       = rng.normal(0, 0.3, (n_pothole, 3))
    acc_pothole[:, 2] += rng.uniform(13.0, 17.0, n_pothole)   # impact spike

    gyro_pothole = rng.normal(0, 0.15, (n_pothole, 3))

    df_pothole = pd.DataFrame({
        "acc_x":  acc_pothole[:, 0], "acc_y": acc_pothole[:, 1], "acc_z": acc_pothole[:, 2],
        "gyro_x": gyro_pothole[:, 0], "gyro_y": gyro_pothole[:, 1], "gyro_z": gyro_pothole[:, 2],
        "label":  1,
    })

    # Interleave blocks of WINDOW_SIZE so each window is homogeneous
    chunks_normal  = [df_normal.iloc[i:i+WINDOW_SIZE]  for i in range(0, n_normal,  WINDOW_SIZE)]
    chunks_pothole = [df_pothole.iloc[i:i+WINDOW_SIZE] for i in range(0, n_pothole, WINDOW_SIZE)]

    # Interleave: ~2 normal for every 1 pothole (≈ 70/30 ratio)
    combined = []
    pi = 0
    for ni, nc in enumerate(chunks_normal):
        combined.append(nc)
        if ni % 2 == 1 and pi < len(chunks_pothole):
            combined.append(chunks_pothole[pi])
            pi += 1
    while pi < len(chunks_pothole):
        combined.append(chunks_pothole[pi])
        pi += 1

    df = pd.concat(combined, ignore_index=True)
    actual_pothole = int(df["label"].sum())
    actual_normal  = len(df) - actual_pothole
    print(f"[SYNTHETIC] Generated {len(df)} samples (normal={actual_normal}, pothole={actual_pothole})")
    return df


# ─── Main ─────────────────────────────────────────────────────────────────────

def main(csv_path: str | None = None):
    os.makedirs(RESULTS_DIR, exist_ok=True)

    # 1. Load dataset
    if csv_path is None:
        csv_path = find_dataset(DATA_PATHS)

    synthetic = False
    if csv_path and os.path.exists(csv_path):
        print(f"[IMU] Loading real dataset from: {csv_path}")
        df = load_imu_data(csv_path)
    else:
        print("[IMU] Real dataset not found — using SYNTHETIC dataset for demonstration.")
        print("      To use real data: pass --csv path/to/imu_data.csv")
        df = generate_synthetic_dataset()
        synthetic = True

    print(f"[IMU] Dataset: {len(df)} samples  |  Potholes: {df['label'].sum()}  |  Normal: {(df['label']==0).sum()}")

    # 2. Segment into windows
    windows = segment_windows(df)
    print(f"[IMU] Windows: {len(windows)}  (size={WINDOW_SIZE}, step={STEP_SIZE})")

    y_true = [lbl for (_, lbl) in windows]

    # 3. Threshold sweep
    print("\n[IMU] Threshold sweep:")
    print(f"  {'Threshold':>10}  {'Precision':>10}  {'Recall':>10}  {'F1':>10}  {'Accuracy':>10}")
    print("  " + "-" * 60)

    best_f1       = -1.0
    best_threshold = THRESHOLDS[0]
    best_metrics   = {}
    all_threshold_results = []

    for thr in THRESHOLDS:
        y_pred = [compute_imu_score(w, thr) for (w, _) in windows]

        prec = precision_score(y_true, y_pred, zero_division=0)
        rec  = recall_score(   y_true, y_pred, zero_division=0)
        f1   = f1_score(       y_true, y_pred, zero_division=0)
        acc  = accuracy_score( y_true, y_pred)

        marker = " ← best" if f1 > best_f1 else ""
        print(f"  {thr:>10.1f}  {prec:>10.4f}  {rec:>10.4f}  {f1:>10.4f}  {acc:>10.4f}{marker}")

        all_threshold_results.append({
            "threshold": thr, "precision": round(prec, 4),
            "recall": round(rec, 4), "f1": round(f1, 4), "accuracy": round(acc, 4),
        })

        if f1 > best_f1:
            best_f1        = f1
            best_threshold = thr
            best_metrics   = {"precision": prec, "recall": rec, "f1": f1, "accuracy": acc}
            best_y_pred    = y_pred

    # 4. Print best result
    print(f"\n[IMU] ✓ Best threshold: {best_threshold} m/s²")
    print(f"  Precision : {best_metrics['precision']:.4f}")
    print(f"  Recall    : {best_metrics['recall']:.4f}")
    print(f"  F1-Score  : {best_metrics['f1']:.4f}")
    print(f"  Accuracy  : {best_metrics['accuracy']:.4f}")

    # 5. Save results
    output = {
        "branch":           "IMU",
        "dataset":          csv_path if not synthetic else "SYNTHETIC",
        "synthetic":        synthetic,
        "n_samples":        len(df),
        "n_windows":        len(windows),
        "window_size":      WINDOW_SIZE,
        "step_size":        STEP_SIZE,
        "best_threshold":   best_threshold,
        "best_metrics": {
            "precision": round(best_metrics["precision"], 4),
            "recall":    round(best_metrics["recall"],    4),
            "f1":        round(best_metrics["f1"],        4),
            "accuracy":  round(best_metrics["accuracy"],  4),
        },
        "threshold_sweep":  all_threshold_results,
    }

    with open(OUTPUT_FILE, "w") as f:
        json.dump(output, f, indent=2)

    print(f"\n[IMU] Results saved → {OUTPUT_FILE}")
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RoadGuard — IMU Branch Evaluation")
    parser.add_argument("--csv", type=str, default=None,
                        help="Path to the Thessaloniki IMU CSV file")
    args = parser.parse_args()
    main(csv_path=args.csv)
