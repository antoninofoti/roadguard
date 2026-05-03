"""
fl_partition.py
===============
Non-IID federated partitioning for the RoadGuard evaluation pipeline.

Dataset:
  - IMU: CSV with columns [accx, accy, accz, gyrox, gyroy, gyroz, label]
         loaded through eval_imu_branch.load_imu_data()
  - Vision: frame labels from CSV loaded through eval_vision_branch.load_frame_labels()

Partitioning:
  - K clients = 5
  - Dirichlet alpha = 0.5
  - Strong non-IID split that simulates geographic heterogeneity across users

Output:
  - CSV/JSON partition files under evaluation/results/fl_partitions/
  - Partition summary JSON
  - Dark-theme plot of client class distributions
"""

import os
import json
import argparse
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from eval_imu_branch import load_imu_data, generate_synthetic_dataset
from eval_vision_branch import load_frame_labels, generate_synthetic_frames_and_labels

# ─── Constants ────────────────────────────────────────────────────────────────
K_CLIENTS = 5          # number of federated clients
DIRICHLET_ALPHA = 0.5  # concentration: <1 = strong non-IID, >1 = more uniform
RANDOM_SEED = 42
PARTITIONS_DIR = os.path.join(os.path.dirname(__file__), "results", "fl_partitions")

IMU_CSV_PATHS = [
    "data/thessaloniki/imu_normalized.csv",
    "data/thessaloniki/road_quality_data.csv",
    "data/thessaloniki/data.csv",
]

VISION_LABEL_CSV_PATHS = [
    "data/thessaloniki/frame_labels.csv",
    "data/thessaloniki/labels.csv",
]


# ─── Helpers ──────────────────────────────────────────────────────────────────

def find_dataset(paths: list[str]) -> str | None:
    """Return the first existing path from the candidate list."""
    for p in paths:
        if os.path.exists(p):
            return p
    return None


def _dirichlet_multinomial_split(n_items: int, k: int, alpha: float, rng: np.random.Generator) -> np.ndarray:
    """Sample client counts from a Dirichlet distribution and close the totals exactly."""
    proportions = rng.dirichlet([alpha] * k)
    counts = rng.multinomial(n_items, proportions)
    return counts


def _split_indices_by_label(indices: np.ndarray, k: int, alpha: float, rng: np.random.Generator) -> list[list[int]]:
    """Assign a label-specific pool to clients using Dirichlet-sampled proportions."""
    client_buckets: list[list[int]] = [[] for _ in range(k)]
    if len(indices) == 0:
        return client_buckets

    shuffled = np.array(indices, copy=True)
    rng.shuffle(shuffled)
    counts = _dirichlet_multinomial_split(len(shuffled), k, alpha, rng)

    start = 0
    for client_id, count in enumerate(counts):
        end = start + int(count)
        client_buckets[client_id].extend(shuffled[start:end].tolist())
        start = end

    return client_buckets


def partition_imu_dirichlet(df: pd.DataFrame, k: int, alpha: float, seed: int) -> list[list[int]]:
    """
    Partition the IMU dataset into K non-IID client splits with Dirichlet sampling.

    Each client receives a different mix of label=0 and label=1 samples while
    preserving the full dataset coverage.
    """
    rng = np.random.default_rng(seed)

    label_0_indices = df.index[df["label"] == 0].to_numpy()
    label_1_indices = df.index[df["label"] == 1].to_numpy()

    partitions = [[] for _ in range(k)]

    zero_splits = _split_indices_by_label(label_0_indices, k, alpha, rng)
    one_splits = _split_indices_by_label(label_1_indices, k, alpha, rng)

    for client_id in range(k):
        partitions[client_id].extend(zero_splits[client_id])
        partitions[client_id].extend(one_splits[client_id])
        rng.shuffle(partitions[client_id])

    total_assigned = sum(len(bucket) for bucket in partitions)
    if total_assigned != len(df):
        raise RuntimeError(f"IMU partitioning mismatch: assigned={total_assigned}, expected={len(df)}")

    for client_id, indices in enumerate(partitions):
        client_df = df.loc[indices]
        n_samples = len(client_df)
        n_pothole = int((client_df["label"] == 1).sum())
        n_normal = int((client_df["label"] == 0).sum())
        pothole_ratio = (n_pothole / n_samples) if n_samples else 0.0
        print(
            f"[IMU][Client {client_id}] n_samples={n_samples} "
            f"n_pothole={n_pothole} n_normal={n_normal} pothole_ratio={pothole_ratio:.3f}"
        )

    return partitions


def partition_vision_dirichlet(frame_labels: dict[str, int], k: int, alpha: float, seed: int) -> list[list[str]]:
    """
    Partition the vision labels into K non-IID client splits with Dirichlet sampling.
    """
    rng = np.random.default_rng(seed)

    label_0_ids = np.array([frame_id for frame_id, label in frame_labels.items() if int(label) == 0], dtype=object)
    label_1_ids = np.array([frame_id for frame_id, label in frame_labels.items() if int(label) == 1], dtype=object)

    partitions = [[] for _ in range(k)]

    zero_splits = _split_indices_by_label(np.arange(len(label_0_ids)), k, alpha, rng)
    one_splits = _split_indices_by_label(np.arange(len(label_1_ids)), k, alpha, rng)

    for client_id in range(k):
        partitions[client_id].extend(label_0_ids[zero_splits[client_id]].tolist())
        partitions[client_id].extend(label_1_ids[one_splits[client_id]].tolist())
        rng.shuffle(partitions[client_id])

    total_assigned = sum(len(bucket) for bucket in partitions)
    if total_assigned != len(frame_labels):
        raise RuntimeError(f"Vision partitioning mismatch: assigned={total_assigned}, expected={len(frame_labels)}")

    for client_id, frame_ids in enumerate(partitions):
        n_frames = len(frame_ids)
        n_pothole = sum(int(frame_labels[fid]) for fid in frame_ids)
        n_normal = n_frames - n_pothole
        pothole_ratio = (n_pothole / n_frames) if n_frames else 0.0
        print(
            f"[Vision][Client {client_id}] n_frames={n_frames} "
            f"n_pothole={n_pothole} n_normal={n_normal} pothole_ratio={pothole_ratio:.3f}"
        )

    return partitions


def compute_heterogeneity(partitions_imu: list[list[int]], df_imu: pd.DataFrame) -> float:
    """
    Compute a simple heterogeneity score from the spread of client pothole ratios.

    If SciPy is available, the function also computes pairwise Earth Mover's
    Distance over the binary class distributions, but the returned score remains
    the standard deviation of the pothole ratios for stability and readability.
    """
    pothole_ratios = []
    for indices in partitions_imu:
        client_df = df_imu.loc[indices]
        ratio = float((client_df["label"] == 1).mean()) if len(client_df) else 0.0
        pothole_ratios.append(ratio)

    heterogeneity_score = float(np.std(pothole_ratios)) if pothole_ratios else 0.0

    emd_score = None
    try:
        from scipy.stats import wasserstein_distance

        distributions = [np.array([1.0 - r, r], dtype=float) for r in pothole_ratios]
        pairwise = []
        for i in range(len(distributions)):
            for j in range(i + 1, len(distributions)):
                pairwise.append(float(wasserstein_distance([0, 1], [0, 1], distributions[i], distributions[j])))
        emd_score = float(np.mean(pairwise)) if pairwise else 0.0
    except Exception:
        emd_score = None

    if emd_score is None:
        print(f"Heterogeneity score (std of pothole_ratio): {heterogeneity_score:.3f}")
    else:
        print(
            f"Heterogeneity score (std of pothole_ratio): {heterogeneity_score:.3f} "
            f"| mean pairwise EMD: {emd_score:.3f}"
        )

    return heterogeneity_score


def save_partitions(
    imu_indices: list[list[int]],
    vision_indices: list[list[str]],
    df_imu: pd.DataFrame,
    heterogeneity_score: float,
) -> dict:
    """Save client partitions and a summary JSON to disk."""
    os.makedirs(PARTITIONS_DIR, exist_ok=True)

    clients = []
    for client_id in range(K_CLIENTS):
        imu_client_df = df_imu.loc[imu_indices[client_id]].copy()
        imu_client_df.to_csv(os.path.join(PARTITIONS_DIR, f"imu_client_{client_id}.csv"), index=False)

        vision_path = os.path.join(PARTITIONS_DIR, f"vision_client_{client_id}.json")
        with open(vision_path, "w", encoding="utf-8") as f:
            json.dump(vision_indices[client_id], f, indent=2)

        n_imu_samples = len(imu_client_df)
        n_pothole = int((imu_client_df["label"] == 1).sum()) if n_imu_samples else 0
        pothole_ratio = (n_pothole / n_imu_samples) if n_imu_samples else 0.0

        clients.append(
            {
                "client_id": client_id,
                "n_imu_samples": n_imu_samples,
                "pothole_ratio": round(float(pothole_ratio), 6),
                "n_vision_frames": len(vision_indices[client_id]),
            }
        )

    summary = {
        "k_clients": K_CLIENTS,
        "dirichlet_alpha": DIRICHLET_ALPHA,
        "heterogeneity_score": round(float(heterogeneity_score), 6),
        "clients": clients,
    }

    summary_path = os.path.join(PARTITIONS_DIR, "partition_summary.json")
    with open(summary_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)

    print(f"[Save] Partition summary saved -> {summary_path}")
    return summary


def plot_partition_distribution(partitions_imu: list[list[int]], df_imu: pd.DataFrame, save_path: str) -> None:
    """Plot the class distribution for each federated client with a dark theme."""
    plt.style.use("dark_background")

    client_ids = list(range(len(partitions_imu)))
    normal_counts = []
    pothole_counts = []

    for indices in partitions_imu:
        client_df = df_imu.loc[indices]
        normal_counts.append(int((client_df["label"] == 0).sum()))
        pothole_counts.append(int((client_df["label"] == 1).sum()))

    x = np.arange(len(client_ids))
    width = 0.36

    fig, ax = plt.subplots(figsize=(10, 6))
    ax.bar(x - width / 2, normal_counts, width, label="label=0 (normal)", color="#4FC3F7")
    ax.bar(x + width / 2, pothole_counts, width, label="label=1 (pothole)", color="#EF5350")

    ax.set_title("Non-IID Data Distribution across FL Clients (Dirichlet α=0.5)")
    ax.set_xlabel("Client")
    ax.set_ylabel("Number of samples")
    ax.set_xticks(x)
    ax.set_xticklabels([f"Client {i}" for i in client_ids])
    ax.legend()
    ax.grid(axis="y", alpha=0.2)
    fig.tight_layout()
    fig.savefig(save_path, dpi=150)
    plt.close(fig)

    print(f"[Plot] Partition distribution saved -> {save_path}")


def _load_imu_dataset(imu_csv: str | None = None) -> tuple[pd.DataFrame, bool]:
    """Load the IMU dataset or fall back to a synthetic dataset."""
    if imu_csv is None:
        imu_csv = find_dataset(IMU_CSV_PATHS)

    if imu_csv and os.path.exists(imu_csv):
        print(f"[IMU] Loading dataset from: {imu_csv}")
        return load_imu_data(imu_csv), False

    print("[IMU] Real dataset not found — using SYNTHETIC dataset for partitioning.")
    return generate_synthetic_dataset(), True


def _load_vision_labels(vision_csv: str | None = None) -> tuple[dict[str, int], bool]:
    """Load frame labels or fall back to a synthetic label dictionary."""
    if vision_csv is None:
        vision_csv = find_dataset(VISION_LABEL_CSV_PATHS)

    if vision_csv and os.path.exists(vision_csv):
        print(f"[Vision] Loading frame labels from: {vision_csv}")
        return load_frame_labels(vision_csv), False

    print("[Vision] Label CSV not found — using SYNTHETIC frame labels for partitioning.")
    return generate_synthetic_frames_and_labels(os.path.join("data", "thessaloniki", "frames")), True


# ─── Main ─────────────────────────────────────────────────────────────────────

def main(imu_csv: str | None = None, vision_csv: str | None = None):
    os.makedirs(PARTITIONS_DIR, exist_ok=True)

    df_imu, imu_synthetic = _load_imu_dataset(imu_csv)
    frame_labels, vision_synthetic = _load_vision_labels(vision_csv)

    print(f"[IMU] Samples: {len(df_imu)}  |  Potholes: {int(df_imu['label'].sum())}  |  Normal: {int((df_imu['label'] == 0).sum())}")
    print(f"[Vision] Frames: {len(frame_labels)}  |  Potholes: {int(sum(frame_labels.values()))}  |  Normal: {int(len(frame_labels) - sum(frame_labels.values()))}")

    partitions_imu = partition_imu_dirichlet(df_imu, K_CLIENTS, DIRICHLET_ALPHA, RANDOM_SEED)
    partitions_vision = partition_vision_dirichlet(frame_labels, K_CLIENTS, DIRICHLET_ALPHA, RANDOM_SEED)

    heterogeneity_score = compute_heterogeneity(partitions_imu, df_imu)
    summary = save_partitions(partitions_imu, partitions_vision, df_imu, heterogeneity_score)

    plot_path = os.path.join(PARTITIONS_DIR, "partition_distribution.png")
    plot_partition_distribution(partitions_imu, df_imu, plot_path)

    summary["synthetic_imu"] = imu_synthetic
    summary["synthetic_vision"] = vision_synthetic
    summary["partition_plot"] = plot_path

    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="RoadGuard — Non-IID FL partitioning with Dirichlet sampling"
    )
    parser.add_argument("--imu-csv", type=str, default=None, help="Path to Thessaloniki IMU CSV file")
    parser.add_argument("--vision-csv", type=str, default=None, help="Path to Thessaloniki frame labels CSV file")
    args = parser.parse_args()

    result = main(imu_csv=args.imu_csv, vision_csv=args.vision_csv)
    print(json.dumps(result, indent=2))