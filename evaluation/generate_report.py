"""
generate_report.py
==================
Reads all branch evaluation JSON files and produces:
  1. comparison_table.csv   — tabular metrics comparison
  2. comparison_chart.png   — F1 bar chart for the thesis presentation
  3. Console printout of the full comparison table + best fusion weights.

Usage:
    python evaluation/generate_report.py
"""

import os
import json
import csv
import argparse

import numpy as np
import matplotlib
matplotlib.use("Agg")  # headless / no display required
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

# ─── Constants ────────────────────────────────────────────────────────────────
RESULTS_DIR  = os.path.join(os.path.dirname(__file__), "results")
IMU_JSON     = os.path.join(RESULTS_DIR, "imu_branch_metrics.json")
VISION_JSON  = os.path.join(RESULTS_DIR, "vision_branch_metrics.json")
FUSION_JSON  = os.path.join(RESULTS_DIR, "fusion_metrics.json")

TABLE_CSV    = os.path.join(RESULTS_DIR, "comparison_table.csv")
CHART_PNG    = os.path.join(RESULTS_DIR, "comparison_chart.png")

# ─── Palette ─────────────────────────────────────────────────────────────────
# RoadGuard brand-aligned dark theme
BG_COLOR   = "#0f1117"
CARD_COLOR = "#1a1d27"
TEXT_COLOR = "#e8eaf6"
GRID_COLOR = "#2a2d3e"

COLOR_IMU    = "#5c6bc0"   # indigo
COLOR_VISION = "#26a69a"   # teal
COLOR_FUSION = "#ef5350"   # coral-red (stands out as the best result)

# ─── Helpers ──────────────────────────────────────────────────────────────────

def load_json(path: str) -> dict:
    with open(path) as f:
        return json.load(f)


def metrics_row(label: str, m: dict) -> dict:
    return {
        "Branch":    label,
        "Precision": m["precision"],
        "Recall":    m["recall"],
        "F1-Score":  m["f1"],
        "Accuracy":  m["accuracy"],
    }


def print_table(rows: list[dict]):
    header = ["Branch", "Precision", "Recall", "F1-Score", "Accuracy"]
    col_w  = [16, 12, 12, 12, 12]

    # Header
    line = "  ".join(f"{h:<{w}}" for h, w in zip(header, col_w))
    print("\n" + "─" * len(line))
    print(line)
    print("─" * len(line))

    for row in rows:
        line = (
            f"{row['Branch']:<{col_w[0]}}"
            f"  {row['Precision']:>{col_w[1]-2}.4f}  "
            f"  {row['Recall']:>{col_w[2]-2}.4f}  "
            f"  {row['F1-Score']:>{col_w[3]-2}.4f}  "
            f"  {row['Accuracy']:>{col_w[4]-2}.4f}"
        )
        print(line)

    print("─" * len(line))


def save_csv(rows: list[dict], path: str):
    fieldnames = ["Branch", "Precision", "Recall", "F1-Score", "Accuracy"]
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    print(f"[Report] CSV saved  → {path}")


def generate_chart(rows: list[dict], best_fusion: dict, path: str):
    """Generate a premium dark-theme bar chart for all four metrics."""
    branches = [r["Branch"] for r in rows]
    metrics  = ["Precision", "Recall", "F1-Score", "Accuracy"]

    values   = {
        m: [r[m] for r in rows]
        for m in metrics
    }

    n_branches = len(branches)
    n_metrics  = len(metrics)
    x          = np.arange(n_branches)
    bar_width  = 0.18
    offsets    = np.linspace(-(n_metrics - 1) / 2, (n_metrics - 1) / 2, n_metrics) * bar_width

    # Color map per metric
    metric_colors = ["#5c6bc0", "#26a69a", "#ef5350", "#ffa726"]
    branch_colors = [COLOR_IMU, COLOR_VISION, COLOR_FUSION]

    fig, axes = plt.subplots(1, 2, figsize=(16, 7), facecolor=BG_COLOR)

    # ── Left panel: grouped bar per metric ───────────────────────────────────
    ax1 = axes[0]
    ax1.set_facecolor(CARD_COLOR)
    ax1.set_title("All Metrics by Branch", color=TEXT_COLOR, fontsize=14, pad=12, fontweight="bold")

    for i, (metric, color) in enumerate(zip(metrics, metric_colors)):
        bars = ax1.bar(
            x + offsets[i],
            values[metric],
            width=bar_width,
            label=metric,
            color=color,
            alpha=0.85,
            zorder=3,
        )
        for bar, val in zip(bars, values[metric]):
            ax1.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height() + 0.01,
                f"{val:.3f}",
                ha="center", va="bottom",
                fontsize=7.5, color=TEXT_COLOR, fontweight="bold"
            )

    ax1.set_xticks(x)
    ax1.set_xticklabels(branches, color=TEXT_COLOR, fontsize=11)
    ax1.set_ylim(0, 1.15)
    ax1.set_ylabel("Score", color=TEXT_COLOR, fontsize=11)
    ax1.tick_params(colors=TEXT_COLOR)
    ax1.legend(facecolor=CARD_COLOR, labelcolor=TEXT_COLOR, fontsize=9, loc="upper left")
    ax1.grid(axis="y", color=GRID_COLOR, linewidth=0.7, zorder=0)
    for spine in ax1.spines.values():
        spine.set_edgecolor(GRID_COLOR)

    # ── Right panel: F1 highlight chart ──────────────────────────────────────
    ax2 = axes[1]
    ax2.set_facecolor(CARD_COLOR)
    ax2.set_title("F1-Score Comparison (Thesis Key Result)", color=TEXT_COLOR, fontsize=14, pad=12, fontweight="bold")

    f1_values = values["F1-Score"]
    bars = ax2.bar(
        branches,
        f1_values,
        color=branch_colors,
        width=0.45,
        alpha=0.88,
        zorder=3,
        edgecolor=BG_COLOR,
        linewidth=1.5,
    )

    best_f1 = max(f1_values)
    for bar, val, branch in zip(bars, f1_values, branches):
        ax2.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + 0.015,
            f"{val:.4f}",
            ha="center", va="bottom",
            fontsize=13, color=TEXT_COLOR, fontweight="bold"
        )
        if val == best_f1:
            ax2.text(
                bar.get_x() + bar.get_width() / 2,
                val / 2,
                "★ BEST",
                ha="center", va="center",
                fontsize=10, color="white", fontweight="bold",
                alpha=0.85,
            )

    # Delta annotations
    fusion_f1  = f1_values[2]
    imu_f1     = f1_values[0]
    vision_f1  = f1_values[1]
    d_imu    = (fusion_f1 - imu_f1)    * 100
    d_vision = (fusion_f1 - vision_f1) * 100

    annotation_text = (
        f"Fusion vs IMU:    {d_imu:+.1f} pp\n"
        f"Fusion vs Vision: {d_vision:+.1f} pp\n"
        f"Best α(CV)={best_fusion.get('alpha',0.55)}  "
        f"β(IMU)={best_fusion.get('beta',0.30)}  "
        f"γ(temp)={best_fusion.get('gamma',0.15)}"
    )
    ax2.text(
        0.97, 0.04, annotation_text,
        transform=ax2.transAxes,
        fontsize=9, color="#a5d6a7",
        ha="right", va="bottom",
        bbox=dict(boxstyle="round,pad=0.5", facecolor=BG_COLOR, alpha=0.8, edgecolor=GRID_COLOR)
    )

    ax2.set_ylim(0, 1.15)
    ax2.set_ylabel("F1-Score", color=TEXT_COLOR, fontsize=11)
    ax2.tick_params(colors=TEXT_COLOR)
    ax2.grid(axis="y", color=GRID_COLOR, linewidth=0.7, zorder=0)
    for spine in ax2.spines.values():
        spine.set_edgecolor(GRID_COLOR)

    # Figure-level metadata
    fig.suptitle(
        "RoadGuard — Late Fusion Evaluation  |  Thessaloniki Road Quality Dataset",
        color=TEXT_COLOR, fontsize=13, y=1.01, fontweight="bold"
    )

    # Watermark note if synthetic
    fig.text(
        0.5, -0.02,
        "Note: Results obtained on synthetic data if real Thessaloniki dataset not loaded.",
        ha="center", color="#888", fontsize=8
    )

    plt.tight_layout()
    fig.savefig(path, dpi=150, bbox_inches="tight", facecolor=BG_COLOR)
    plt.close(fig)
    print(f"[Report] Chart saved → {path}")


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    os.makedirs(RESULTS_DIR, exist_ok=True)

    # Load JSON results
    missing = [p for p in [IMU_JSON, VISION_JSON, FUSION_JSON] if not os.path.exists(p)]
    if missing:
        print("[Report] Missing result files:")
        for p in missing:
            print(f"  ✗ {p}")
        print("\nPlease run the evaluation pipeline first:")
        print("  python evaluation/run_all.py")
        return

    imu_data    = load_json(IMU_JSON)
    vision_data = load_json(VISION_JSON)
    fusion_data = load_json(FUSION_JSON)

    # Build table rows
    rows = [
        metrics_row("IMU-only",      imu_data["best_metrics"]),
        metrics_row("Vision-only",   vision_data["best_metrics"]),
        metrics_row("Late Fusion",   fusion_data["best_metrics"]),
    ]

    # Print table
    print("\n╔══════════════════════════════════════════════════════════╗")
    print("║      RoadGuard — Branch Comparison Table                  ║")
    print("╚══════════════════════════════════════════════════════════╝")
    print_table(rows)

    # Alpha sweep summary
    best = fusion_data["best_metrics"]
    print(f"\n[Fusion] Best weights: α(CV)={best['alpha']}  β(IMU)={best['beta']}  γ(temp)={best['gamma']}")

    sweep = fusion_data.get("weight_sweep", [])
    if sweep:
        print("\n[Fusion] Full alpha sweep:")
        print(f"  {'α(CV)':>8}  {'β(IMU)':>8}  {'F1':>10}")
        for s in sweep:
            marker = " ← best" if s["alpha"] == best["alpha"] else ""
            print(f"  {s['alpha']:>8.2f}  {s['beta']:>8.2f}  {s['f1']:>10.4f}{marker}")

    # Comparison summary
    cmp = fusion_data.get("comparison", {})
    print(f"\n[Fusion] ═══ KEY RESULT ════════════════════════════════════")
    print(f"  IMU-only  F1 : {cmp.get('imu_f1',   0):.4f}")
    print(f"  Vision-only F1 : {cmp.get('vision_f1', 0):.4f}")
    print(f"  Late Fusion F1 : {cmp.get('fusion_f1', 0):.4f}")
    print(f"  Δ vs IMU       : {cmp.get('delta_vs_imu_pp',    0):+.1f} percentage points")
    print(f"  Δ vs Vision    : {cmp.get('delta_vs_vision_pp', 0):+.1f} percentage points")

    # Save CSV
    save_csv(rows, TABLE_CSV)

    # Generate chart
    generate_chart(rows, best, CHART_PNG)

    print("\n[Report] ✓ Pipeline complete.")
    print(f"  {TABLE_CSV}")
    print(f"  {CHART_PNG}")


if __name__ == "__main__":
    main()
