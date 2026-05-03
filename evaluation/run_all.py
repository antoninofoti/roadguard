"""
run_all.py
==========
Master orchestration script for the RoadGuard late-fusion evaluation pipeline.

Classical evaluation branch (always run):
  1. eval_imu_branch.py
  2. eval_vision_branch.py
  3. eval_late_fusion.py
  4. generate_report.py

Federated Learning extension (optional, use --skip-fl to skip):
  5. fl_partition.py               (Non-IID FL partitioning with Dirichlet)
  6. fl_fedavg.py                  (FedAvg simulation on YOLOv8n)
  7. fl_personalized_fusion.py     (Per-client personalized fusion weights learning)
  8. fl_report.py                  (FL-specific report generation)

Usage:
    python evaluation/run_all.py [--csv PATH] [--model PATH] [--frames PATH] [--skip-fl]

Arguments:
    --csv    Path to Thessaloniki IMU CSV
             (default: auto-detect in data/thessaloniki/)
    --model  Path to fine-tuned YOLOv8 .pt weights
             (default: ml/runs/roadguard_v1/weights/best.pt)
    --frames Path to Thessaloniki frames directory
             (default: data/thessaloniki/frames/)
    --skip-fl Skip federated learning steps (5-8) for quick demo
"""

import sys
import os
import argparse
import time

# Ensure evaluation/ is importable from project root
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))

# ─── Imports ──────────────────────────────────────────────────────────────────
from eval_imu_branch           import main as run_imu
from eval_vision_branch        import main as run_vision
from eval_late_fusion          import main as run_fusion
from generate_report           import main as run_report
from fl_partition              import main as run_fl_partition
from fl_fedavg                 import main as run_fl_fedavg
from fl_personalized_fusion    import main as run_fl_personalized_fusion
from fl_report                 import main as run_fl_report


def banner(title: str):
    width = 64
    print("\n" + "═" * width)
    print(f"  {title}")
    print("═" * width)


def main():
    parser = argparse.ArgumentParser(
        description="RoadGuard — Full Late-Fusion Evaluation Pipeline with Optional FL Extension"
    )
    parser.add_argument("--csv",    type=str, default=None,
                        help="Path to Thessaloniki IMU CSV file")
    parser.add_argument("--model",  type=str, default=None,
                        help="Path to fine-tuned YOLOv8 .pt weights")
    parser.add_argument("--frames", type=str, default=None,
                        help="Path to frames directory")
    parser.add_argument("--skip-fl", action="store_true",
                        help="Skip federated learning steps (5-8) for quick demo")
    args = parser.parse_args()

    overall_start = time.time()
    
    # Determine total steps
    total_steps = 4 if args.skip_fl else 8

    # ── Step 1: IMU Branch ────────────────────────────────────────────────────
    banner(f"Step 1/{total_steps} — IMU Branch Evaluation")
    t0 = time.time()
    imu_results = run_imu(csv_path=args.csv)
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ── Step 2: Vision Branch ─────────────────────────────────────────────────
    banner(f"Step 2/{total_steps} — Vision Branch Evaluation")
    t0 = time.time()
    vision_results = run_vision(model_path=args.model, frames_dir=args.frames)
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ── Step 3: Late Fusion ───────────────────────────────────────────────────
    banner(f"Step 3/{total_steps} — Late Fusion Evaluation")
    t0 = time.time()
    fusion_results = run_fusion()
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ── Step 4: Report ────────────────────────────────────────────────────────
    banner(f"Step 4/{total_steps} — Generating Report & Chart")
    t0 = time.time()
    run_report()
    print(f"  ✓ Completed in {time.time() - t0:.1f}s")

    # ─── FL Pipeline (Optional) ──────────────────────────────────────────────
    partition_results = None
    fedavg_results = None
    personalized_results = None

    if not args.skip_fl:
        # ── Step 5: FL Partitioning ───────────────────────────────────────
        banner("Step 5/8 — Non-IID FL Partitioning (Dirichlet)")
        t0 = time.time()
        partition_results = run_fl_partition(imu_csv=args.csv, vision_csv=None)
        print(f"  ✓ Completed in {time.time() - t0:.1f}s")

        # ── Step 6: FL FedAvg ──────────────────────────────────────────
        banner("Step 6/8 — FedAvg Simulation (YOLOv8n Vision)")
        t0 = time.time()
        fedavg_results = run_fl_fedavg()
        print(f"  ✓ Completed in {time.time() - t0:.1f}s")

        # ── Step 7: FL Personalized Fusion ─────────────────────────────
        banner("Step 7/8 — Federated Personalized Fusion Weights")
        t0 = time.time()
        personalized_results = run_fl_personalized_fusion()
        print(f"  ✓ Completed in {time.time() - t0:.1f}s")

        # ── Step 8: FL Report ──────────────────────────────────────────
        banner("Step 8/8 — Federated Learning Report Generation")
        t0 = time.time()
        fl_report_results = run_fl_report()
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

    # ── FL Results Summary (if not skipped) ────────────────────────────────────
    if not args.skip_fl and partition_results and fedavg_results and personalized_results:
        print("\n  ╔════════════════════════════════════════════════════════════╗")
        print("  ║  FEDERATED LEARNING EXTENSION RESULTS                      ║")
        print("  ╚════════════════════════════════════════════════════════════╝")

        partition_heterogeneity = partition_results.get("heterogeneity_score", -1)
        n_partition_clients = partition_results.get("k_clients", -1)
        partition_alpha = partition_results.get("dirichlet_alpha", -1)
        print(f"\n  ▶ FL Partitioning: {n_partition_clients} clients, α={partition_alpha}")
        print(f"  ▶ Heterogeneity score (std of pothole_ratio): {partition_heterogeneity:.3f}")

        fedavg_convergence = fedavg_results.get("convergence_ratio", 0)
        fedavg_rounds = fedavg_results.get("config", {}).get("fl_rounds", 0)
        fedavg_final_rounds = fedavg_results.get("rounds", [])
        fedavg_map50 = fedavg_final_rounds[-1]["map50"] if fedavg_final_rounds else 0
        print(f"\n  ▶ FedAvg Convergence: {fedavg_rounds} rounds, mAP50={fedavg_map50:.4f} ({fedavg_convergence*100:.1f}% of baseline)")

        personalized_avg_imp_pp = personalized_results.get("aggregate", {}).get("avg_improvement_pp", 0)
        personalized_n_improved = personalized_results.get("aggregate", {}).get("n_clients_improved", 0)
        personalized_comm_mb = personalized_results.get("aggregate", {}).get("communication_cost", {}).get("total_mb", 0)
        print(f"  ▶ Personalized Fusion: {personalized_avg_imp_pp:+.2f} pp avg improvement, {personalized_n_improved}/{n_partition_clients} clients improved")
        print(f"  ▶ Communication cost: {personalized_comm_mb:.0f} MB")

    # ── Output Files Summary ──────────────────────────────────────────────────
    print("\n  Output files:")
    print("  ├─ evaluation/results/imu_branch_metrics.json")
    print("  ├─ evaluation/results/vision_branch_metrics.json")
    print("  ├─ evaluation/results/fusion_metrics.json")
    print("  ├─ evaluation/results/comparison_table.csv")
    print("  └─ evaluation/results/comparison_chart.png")

    if not args.skip_fl:
        print("  ├─ evaluation/results/fl_partitions/partition_summary.json")
        print("  ├─ evaluation/results/fl_partitions/partition_distribution.png")
        print("  ├─ evaluation/results/fl_partitions/imu_client_*.csv")
        print("  ├─ evaluation/results/fl_partitions/vision_client_*.json")
        print("  ├─ evaluation/results/fl_fedavg_metrics.json")
        print("  ├─ evaluation/results/fl_personalized_fusion_metrics.json")
        print("  ├─ evaluation/results/fl_learning_curve.png")
        print("  ├─ evaluation/results/fl_client_fusion_weights.png")
        print("  ├─ evaluation/results/fl_communication_cost.png")
        print("  └─ evaluation/results/fl_comparison_table.csv")

    print()


if __name__ == "__main__":
    main()
