"""
eval_vision_branch.py
=====================
Evaluation script for the Vision-only branch of the RoadGuard late-fusion pipeline.

Dataset: nickkotarelas/road-quality-dataset (Thessaloniki)
  - Frames located in: data/thessaloniki/frames/
  - Ground-truth labels from: data/thessaloniki/frame_labels.csv
    Columns: [frame_id, label]  where label=1 is pothole, 0 is normal.

Model:
  - YOLOv8n fine-tuned on the RoadGuard figshare pothole dataset
  - Weights: ml/runs/roadguard_v1/weights/best.pt
  - Fallback: yolov8n.pt (base, no fine-tuning)

Scoring:
  - detection_conf = max confidence of any bounding box in a frame
                     (0.0 if no detection)
  - vision_pred    = 1 if detection_conf > 0.5 else 0

Output:
  - Console: Precision, Recall, F1, Accuracy
  - File:    evaluation/results/vision_branch_metrics.json
"""

import os
import json
import argparse
import numpy as np
import pandas as pd
from pathlib import Path
from sklearn.metrics import precision_score, recall_score, f1_score, accuracy_score

# ─── Constants ────────────────────────────────────────────────────────────────
CONF_THRESHOLD   = 0.5
MODEL_PATHS      = [
    "ml/runs/roadguard_v1/weights/best.pt",
    "app/src/main/assets/roadguard_model.tflite",  # fallback: TFLite (not used here)
]
YOLO_BASE        = "yolov8n.pt"
FRAMES_DIR       = "data/thessaloniki/frames"
LABEL_CSV_PATHS  = [
    "data/thessaloniki/frame_labels.csv",
    "data/thessaloniki/labels.csv",
    "data/thessaloniki/ground_truth.csv",
]

RESULTS_DIR = os.path.join(os.path.dirname(__file__), "results")
OUTPUT_FILE = os.path.join(RESULTS_DIR, "vision_branch_metrics.json")

SUPPORTED_EXT = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

# ─── Helpers ──────────────────────────────────────────────────────────────────

def find_model() -> str | None:
    for p in MODEL_PATHS:
        if os.path.exists(p) and p.endswith(".pt"):
            return p
    return None


def find_label_csv() -> str | None:
    for p in LABEL_CSV_PATHS:
        if os.path.exists(p):
            return p
    return None


def load_frame_labels(csv_path: str) -> dict[str, int]:
    """Load frame_id → label mapping from CSV."""
    df = pd.read_csv(csv_path)
    df.columns = [c.strip().lower() for c in df.columns]

    rename = {
        "filename": "frame_id", "file": "frame_id", "image": "frame_id",
        "class": "label", "target": "label", "anomaly": "label",
    }
    df.rename(columns=rename, inplace=True)

    if "frame_id" not in df.columns or "label" not in df.columns:
        raise ValueError(f"CSV must contain 'frame_id' and 'label' columns. Found: {list(df.columns)}")

    df["label"] = pd.to_numeric(df["label"], errors="coerce").fillna(0).astype(int)
    return dict(zip(df["frame_id"].astype(str), df["label"]))


def generate_synthetic_frames_and_labels(frames_dir: str, n: int = 200) -> dict[str, int]:
    """
    Generate synthetic frame labels for CI / demo mode.

    Creates placeholder .jpg filenames and assigns random labels.
    No actual image data is created — the YOLO runner generates
    synthetic predictions instead.
    """
    np.random.seed(42)
    labels = {}
    os.makedirs(frames_dir, exist_ok=True)
    for i in range(n):
        fname = f"frame_{i:04d}.jpg"
        labels[fname] = int(np.random.choice([0, 1], p=[0.7, 0.3]))
    return labels


def run_yolo_on_frames(
    model,
    frames_dir: str,
    frame_labels: dict[str, int],
    synthetic: bool = False,
) -> tuple[list[int], list[int], list[float]]:
    """
    Run YOLOv8 inference on every frame and return aligned
    (y_true, y_pred, confidence) lists.

    In synthetic mode, generates plausible confidence scores without
    loading actual image files.
    """
    y_true  = []
    y_pred  = []
    confs   = []

    rng = np.random.default_rng(seed=42)

    for fname, gt_label in sorted(frame_labels.items()):
        frame_path = os.path.join(frames_dir, fname)

        if synthetic:
            # Simulate model confidence:
            # - If gt=1 (pothole): 65% chance of correct detection, moderate conf
            # - If gt=0 (normal):  10% false-positive rate
            if gt_label == 1:
                conf = float(rng.choice(
                    [rng.uniform(0.55, 0.95), rng.uniform(0.0, 0.45)],
                    p=[0.72, 0.28]
                ))
            else:
                conf = float(rng.choice(
                    [rng.uniform(0.51, 0.80), rng.uniform(0.0, 0.45)],
                    p=[0.12, 0.88]
                ))
        else:
            if not os.path.exists(frame_path):
                # Frame missing — count as no detection
                conf = 0.0
            else:
                results = model(frame_path, verbose=False)
                boxes = results[0].boxes
                if boxes is not None and len(boxes) > 0:
                    conf = float(boxes.conf.max().item())
                else:
                    conf = 0.0

        pred = 1 if conf > CONF_THRESHOLD else 0
        y_true.append(gt_label)
        y_pred.append(pred)
        confs.append(conf)

    return y_true, y_pred, confs


# ─── Main ─────────────────────────────────────────────────────────────────────

def main(model_path: str | None = None, frames_dir: str | None = None):
    os.makedirs(RESULTS_DIR, exist_ok=True)

    # 1. Resolve model
    if model_path is None:
        model_path = find_model()

    synthetic_model = False
    try:
        from ultralytics import YOLO
        if model_path and os.path.exists(model_path):
            print(f"[Vision] Loading fine-tuned model: {model_path}")
            model = YOLO(model_path)
        else:
            print(f"[Vision] Fine-tuned weights not found. Falling back to base YOLOv8n.")
            print(f"         Train first: python ml/train_yolov8.py")
            model = YOLO(YOLO_BASE)
        yolo_available = True
    except ImportError:
        print("[Vision] ultralytics not installed — running in SYNTHETIC mode.")
        model          = None
        yolo_available = False
        synthetic_model = True

    # 2. Resolve frames & labels
    if frames_dir is None:
        frames_dir = FRAMES_DIR

    label_csv = find_label_csv()
    synthetic_data = False

    if label_csv and os.path.exists(label_csv):
        print(f"[Vision] Loading frame labels from: {label_csv}")
        frame_labels = load_frame_labels(label_csv)
    elif os.path.isdir(frames_dir) and any(
        Path(f).suffix.lower() in SUPPORTED_EXT
        for f in os.listdir(frames_dir)
    ):
        # Build labels from directory structure:
        # sub-folders 'pothole/' and 'normal/' expected
        print("[Vision] No label CSV found. Inferring labels from directory structure.")
        frame_labels = {}
        for sub, lbl in [("pothole", 1), ("normal", 0), ("anomaly", 1), ("good", 0)]:
            sub_path = os.path.join(frames_dir, sub)
            if os.path.isdir(sub_path):
                for f in os.listdir(sub_path):
                    if Path(f).suffix.lower() in SUPPORTED_EXT:
                        frame_labels[os.path.join(sub, f)] = lbl
    else:
        print("[Vision] No frames or labels found — using SYNTHETIC dataset for demonstration.")
        print("         To use real data: pass --frames data/thessaloniki/frames")
        frame_labels  = generate_synthetic_frames_and_labels(frames_dir, n=200)
        synthetic_data  = True
        synthetic_model = True   # No real images to run YOLO on

    synthetic = synthetic_data or synthetic_model

    print(f"[Vision] Evaluating {len(frame_labels)} frames  |  "
          f"Potholes: {sum(frame_labels.values())}  |  "
          f"Normal: {sum(1 for v in frame_labels.values() if v == 0)}")

    # 3. Run inference
    print(f"[Vision] Running {'SYNTHETIC' if synthetic else 'YOLOv8'} inference ...")
    y_true, y_pred, confs = run_yolo_on_frames(
        model, frames_dir, frame_labels, synthetic=synthetic
    )

    # 4. Metrics
    prec = precision_score(y_true, y_pred, zero_division=0)
    rec  = recall_score(   y_true, y_pred, zero_division=0)
    f1   = f1_score(       y_true, y_pred, zero_division=0)
    acc  = accuracy_score( y_true, y_pred)

    print(f"\n[Vision] Results:")
    print(f"  Precision : {prec:.4f}")
    print(f"  Recall    : {rec:.4f}")
    print(f"  F1-Score  : {f1:.4f}")
    print(f"  Accuracy  : {acc:.4f}")

    # 5. Save results + per-frame data for fusion script
    output = {
        "branch":       "Vision",
        "model":        model_path if not synthetic_model else "SYNTHETIC",
        "frames_dir":   frames_dir,
        "synthetic":    synthetic,
        "n_frames":     len(frame_labels),
        "conf_threshold": CONF_THRESHOLD,
        "best_metrics": {
            "precision": round(prec, 4),
            "recall":    round(rec,  4),
            "f1":        round(f1,   4),
            "accuracy":  round(acc,  4),
        },
        # Per-frame data needed by eval_late_fusion.py
        "per_frame": [
            {
                "frame_id":  fname,
                "gt_label":  gt,
                "conf":      round(c, 4),
                "pred":      p,
            }
            for fname, gt, c, p in zip(
                sorted(frame_labels.keys()), y_true, confs, y_pred
            )
        ],
    }

    with open(OUTPUT_FILE, "w") as f:
        json.dump(output, f, indent=2)

    print(f"\n[Vision] Results saved → {OUTPUT_FILE}")
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="RoadGuard — Vision Branch Evaluation")
    parser.add_argument("--model",  type=str, default=None,
                        help="Path to YOLOv8 .pt weights file")
    parser.add_argument("--frames", type=str, default=None,
                        help="Path to frames directory")
    args = parser.parse_args()
    main(model_path=args.model, frames_dir=args.frames)
