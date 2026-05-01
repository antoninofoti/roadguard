"""
run_all.py
==========
Master orchestration script for the RoadGuard late-fusion evaluation pipeline.

Runs in order:
  1. eval_imu_branch.py
  2. eval_vision_branch.py
  3. eval_late_fusion.py
  4. generate_report.py

Usage:
    python evaluation/run_all.py [--csv PATH] [--model PATH] [--frames PATH]

Arguments:
    --csv    Path to Thessaloniki IMU CSV
             (default: auto-detect in data/thessaloniki/)
    --model  Path to fine-tuned YOLOv8 .pt weights
             (default: ml/runs/roadguard_v1/weights/best.pt)
    --frames Path to Thessaloniki frames directory
             (default: data/thessaloniki/frames/)
"""

import sys
import os
import argparse
import time

# Ensure evaluation/ is importable from project root
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))

# ─── Imports ──────────────────────────────────────────────────────────────────
from eval_imu_branch    import main as run_imu
from eval_vision_branch import main as run_vision
from eval_late_fusion   import main as run_fusion
from generate_report    import main as run_report


def banner(title: str):
    width = 64
    print("\n" + "═" * width)
    print(f"  {title}")
    print("═" * width)


def main():
    parser = argparse.ArgumentParser(
        description="RoadGuard — Full Late-Fusion Evaluation Pipeline"
    )
    parser.add_argument("--csv",    type=str, default=None,
                        help="Path to Thessaloniki IMU CSV file")
    parser.add_argument("--model",  type=str, default=None,
                        help="Path to fine-tuned YOLOv8 .pt weights")
    parser.add_argument("--frames", type=str, default=None,
                        help="Path to frames directory")
    args = parser.parse_args()

    overall_start = time.time()

    # ── Step 1: IMU Branch ────────────────────────────────────────────────────
    banner("Step 1/4 — IMU Branch Evaluation")
    t0 = time.time()
    imu_results = run_imu(csv_path=args.csv)
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ── Step 2: Vision Branch ─────────────────────────────────────────────────
    banner("Step 2/4 — Vision Branch Evaluation")
    t0 = time.time()
    vision_results = run_vision(model_path=args.model, frames_dir=args.frames)
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ── Step 3: Late Fusion ───────────────────────────────────────────────────
    banner("Step 3/4 — Late Fusion Evaluation")
    t0 = time.time()
    fusion_results = run_fusion()
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ── Step 4: Report ────────────────────────────────────────────────────────
    banner("Step 4/4 — Generating Report & Chart")
    t0 = time.time()
    run_report()
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ── Final Summary ─────────────────────────────────────────────────────────
    total = time.time() - overall_start
    banner(f"Pipeline Complete  ({total:.1f}s total)")

    imu_f1    = imu_results["best_metrics"]["f1"]
    vision_f1 = vision_results["best_metrics"]["f1"]
    fusion_f1 = fusion_results["best_metrics"]["f1"]

    print(f"\n  {'Branch':<18}  {'F1-Score':>10}  {'Precision':>10}  {'Recall':>10}  {'Accuracy':>10}")
    print(f"  {'─'*18}  {'─'*10}  {'─'*10}  {'─'*10}  {'─'*10}")

    for label, data in [
        ("IMU-only",    imu_results),
        ("Vision-only", vision_results),
        ("Late Fusion", fusion_results),
    ]:
        m = data["best_metrics"]
        print(f"  {label:<18}  {m['f1']:>10.4f}  {m['precision']:>10.4f}  {m['recall']:>10.4f}  {m['accuracy']:>10.4f}")

    d_imu    = (fusion_f1 - imu_f1)    * 100
    d_vision = (fusion_f1 - vision_f1) * 100

    print(f"\n  ▶ Late Fusion vs IMU-only:    {d_imu:+.1f} pp F1")
    print(f"  ▶ Late Fusion vs Vision-only: {d_vision:+.1f} pp F1")

    best = fusion_results["best_metrics"]
    print(f"\n  ▶ Optimal fusion weights: α(CV)={best['alpha']}  β(IMU)={best['beta']}  γ(temporal)={best['gamma']}")

    synthetic_note = (
        imu_results.get("synthetic", True) or
        vision_results.get("synthetic", True) or
        fusion_results.get("synthetic", True)
    )
    if synthetic_note:
        print("\n  ⚠ Note: Results computed on SYNTHETIC data.")
        print("    Re-run after downloading the Thessaloniki dataset:")
        print("    kaggle datasets download nickkotarelas/road-quality-dataset -p data/thessaloniki/")
        print("    python evaluation/run_all.py --csv data/thessaloniki/imu_data.csv")

    print("\n  Output files:")
    print("  ├─ evaluation/results/imu_branch_metrics.json")
    print("  ├─ evaluation/results/vision_branch_metrics.json")
    print("  ├─ evaluation/results/fusion_metrics.json")
    print("  ├─ evaluation/results/comparison_table.csv")
    print("  └─ evaluation/results/comparison_chart.png")


if __name__ == "__main__":
    main()
