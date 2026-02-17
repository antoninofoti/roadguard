#!/usr/bin/env python3
"""
Road Quality Dataset Explorer (Kaggle)

Analyzes the synchronized IMU + GPS + Camera dataset.
This is the most critical dataset for sensor fusion calibration.

Expected structure in data/road_quality/:
    - CSV files with IMU readings (accelerometer + gyroscope)
    - GPS coordinate logs
    - Synchronized camera images

Usage:
    python3 explore_road_quality.py
"""

import os
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import matplotlib

matplotlib.use("Agg")  # Non-interactive backend
import matplotlib.pyplot as plt
import seaborn as sns

BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data" / "road_quality"
OUTPUT_DIR = BASE_DIR / "output" / "plots"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def discover_files(data_dir: Path) -> dict:
    """Discover and categorize files in the dataset directory."""
    files = {"csv": [], "images": [], "other": []}

    if not data_dir.exists():
        print(f"ERROR: {data_dir} does not exist. Run setup_datasets.py first.")
        sys.exit(1)

    for f in sorted(data_dir.rglob("*")):
        if f.is_file():
            ext = f.suffix.lower()
            if ext in (".csv", ".tsv"):
                files["csv"].append(f)
            elif ext in (".jpg", ".jpeg", ".png", ".bmp"):
                files["images"].append(f)
            else:
                files["other"].append(f)

    print(f"Found: {len(files['csv'])} CSV, {len(files['images'])} images, {len(files['other'])} other")
    return files


def analyze_imu_data(csv_files: list):
    """Analyze IMU (accelerometer + gyroscope) data from CSV files."""
    if not csv_files:
        print("No CSV files found. Skipping IMU analysis.")
        return

    print("\n--- IMU Data Analysis ---")

    for csv_file in csv_files[:5]:  # Analyze up to 5 files
        print(f"\nReading: {csv_file.name}")
        try:
            df = pd.read_csv(csv_file, nrows=5)
            print(f"  Columns: {list(df.columns)}")
            print(f"  Shape preview: {df.shape}")
        except Exception as e:
            # Try with different separators
            try:
                df = pd.read_csv(csv_file, sep="\t", nrows=5)
                print(f"  Columns (tab-sep): {list(df.columns)}")
            except Exception:
                print(f"  Could not parse: {e}")
                continue

    # Full analysis of first CSV with IMU data
    main_csv = csv_files[0]
    print(f"\nFull analysis of: {main_csv.name}")

    try:
        df = pd.read_csv(main_csv)
    except Exception:
        try:
            df = pd.read_csv(main_csv, sep="\t")
        except Exception as e:
            print(f"  Failed to read: {e}")
            return

    print(f"  Total rows: {len(df)}")
    print(f"  Columns: {list(df.columns)}")
    print(f"\n  Statistics:")
    print(df.describe().to_string())

    # Identify accelerometer and gyroscope columns
    accel_cols = [c for c in df.columns if any(k in c.lower() for k in ["accel", "acc_", "ax", "ay", "az"])]
    gyro_cols = [c for c in df.columns if any(k in c.lower() for k in ["gyro", "gyr_", "gx", "gy", "gz"])]

    if accel_cols:
        print(f"\n  Accelerometer columns: {accel_cols}")
        plot_sensor_data(df, accel_cols, "Accelerometer", "accelerometer_analysis.png")

    if gyro_cols:
        print(f"  Gyroscope columns: {gyro_cols}")
        plot_sensor_data(df, gyro_cols, "Gyroscope", "gyroscope_analysis.png")

    if accel_cols:
        analyze_anomalies(df, accel_cols)

    return df


def plot_sensor_data(df: pd.DataFrame, columns: list, sensor_name: str, filename: str):
    """Create time-series and distribution plots for sensor data."""
    fig, axes = plt.subplots(2, 1, figsize=(14, 8))

    # Time series
    ax1 = axes[0]
    sample_size = min(5000, len(df))
    for col in columns:
        ax1.plot(df[col].iloc[:sample_size], label=col, alpha=0.7, linewidth=0.5)
    ax1.set_title(f"{sensor_name} - Time Series (first {sample_size} samples)")
    ax1.set_xlabel("Sample Index")
    ax1.set_ylabel("Value")
    ax1.legend()
    ax1.grid(True, alpha=0.3)

    # Distribution
    ax2 = axes[1]
    for col in columns:
        ax2.hist(df[col].dropna(), bins=100, alpha=0.5, label=col, density=True)
    ax2.set_title(f"{sensor_name} - Value Distribution")
    ax2.set_xlabel("Value")
    ax2.set_ylabel("Density")
    ax2.legend()
    ax2.grid(True, alpha=0.3)

    plt.tight_layout()
    output_path = OUTPUT_DIR / filename
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved plot: {output_path}")


def analyze_anomalies(df: pd.DataFrame, accel_cols: list):
    """Detect and analyze anomalies in accelerometer data using simple thresholding."""
    print("\n--- Anomaly Detection (Simple Threshold) ---")

    if len(accel_cols) >= 3:
        # Calculate magnitude
        magnitude = np.sqrt(sum(df[c] ** 2 for c in accel_cols[:3]))
    else:
        magnitude = df[accel_cols[0]].abs()

    mean_mag = magnitude.mean()
    std_mag = magnitude.std()
    threshold = mean_mag + 2.5 * std_mag

    anomalies = magnitude > threshold
    n_anomalies = anomalies.sum()

    print(f"  Magnitude stats: mean={mean_mag:.3f}, std={std_mag:.3f}")
    print(f"  Threshold (mean + 2.5σ): {threshold:.3f}")
    print(f"  Anomalies detected: {n_anomalies} / {len(df)} ({100*n_anomalies/len(df):.2f}%)")

    # Plot anomalies
    fig, ax = plt.subplots(figsize=(14, 5))
    sample_size = min(10000, len(df))
    ax.plot(magnitude.iloc[:sample_size], linewidth=0.5, alpha=0.7, label="Magnitude")
    ax.axhline(y=threshold, color="r", linestyle="--", label=f"Threshold ({threshold:.2f})")

    anomaly_idx = np.where(anomalies.iloc[:sample_size])[0]
    ax.scatter(anomaly_idx, magnitude.iloc[anomaly_idx], color="red", s=10, zorder=5, label="Anomalies")

    ax.set_title("Accelerometer Magnitude with Anomaly Detection")
    ax.set_xlabel("Sample Index")
    ax.set_ylabel("Magnitude (m/s²)")
    ax.legend()
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    output_path = OUTPUT_DIR / "anomaly_detection.png"
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved plot: {output_path}")


def analyze_gps_data(df: pd.DataFrame):
    """Analyze and plot GPS trajectory if available."""
    lat_cols = [c for c in df.columns if any(k in c.lower() for k in ["lat", "latitude"])]
    lon_cols = [c for c in df.columns if any(k in c.lower() for k in ["lon", "lng", "longitude"])]

    if lat_cols and lon_cols:
        print("\n--- GPS Trajectory Analysis ---")
        fig, ax = plt.subplots(figsize=(10, 10))
        ax.scatter(df[lon_cols[0]], df[lat_cols[0]], s=1, alpha=0.3, c=range(len(df)), cmap="viridis")
        ax.set_xlabel("Longitude")
        ax.set_ylabel("Latitude")
        ax.set_title("GPS Trajectory")
        ax.set_aspect("equal")
        ax.grid(True, alpha=0.3)

        plt.tight_layout()
        output_path = OUTPUT_DIR / "gps_trajectory.png"
        plt.savefig(output_path, dpi=150, bbox_inches="tight")
        plt.close()
        print(f"  Saved plot: {output_path}")
    else:
        print("  No GPS columns found in the data.")


def main():
    print("=" * 60)
    print("Road Quality Dataset Explorer")
    print("=" * 60)

    files = discover_files(DATA_DIR)

    if not files["csv"] and not files["images"]:
        print("\nNo data files found. Please download the dataset first.")
        print(f"Download: https://www.kaggle.com/datasets/nickkotarelas/road-quality-dataset")
        print(f"Place in: {DATA_DIR}")
        return

    df = analyze_imu_data(files["csv"])

    if df is not None:
        analyze_gps_data(df)

    if files["images"]:
        print(f"\n--- Image Data ---")
        print(f"  Total images: {len(files['images'])}")
        # Show first few image paths
        for img in files["images"][:5]:
            print(f"    {img.name}")

    print("\n" + "=" * 60)
    print(f"Analysis complete. Plots saved to: {OUTPUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    main()
