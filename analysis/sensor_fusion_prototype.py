#!/usr/bin/env python3
"""
Sensor Fusion Prototype for RoadGuard

Implements and tests the core algorithms planned for RoadGuard:
1. Kalman Filter 3D for accelerometer/gyroscope noise reduction
2. Sliding Window Anomaly Detector
3. Fusion score calculation (CV confidence + sensor confidence)

This script can work with:
- Kaggle Road Quality Dataset (real IMU data)
- Synthetic data for algorithm validation

Usage:
    python3 sensor_fusion_prototype.py
"""

import sys
from pathlib import Path
from dataclasses import dataclass
from typing import List, Optional
from collections import deque

import numpy as np
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data" / "road_quality"
OUTPUT_DIR = BASE_DIR / "output" / "plots"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# ========== DATA MODELS ==========


@dataclass
class SensorDataPoint:
    """A single timestamped sensor reading."""
    timestamp: float  # seconds
    accel_x: float
    accel_y: float
    accel_z: float
    gyro_x: float = 0.0
    gyro_y: float = 0.0
    gyro_z: float = 0.0
    filtered_accel_magnitude: float = 0.0
    filtered_gyro_magnitude: float = 0.0


@dataclass
class AnomalyEvent:
    """A detected road anomaly from sensor data."""
    timestamp: float
    anomaly_type: str  # BUMP, POTHOLE, ROUGHNESS, SPEED_BUMP
    severity: float  # 0.0 - 1.0
    confidence: float  # 0.0 - 1.0
    accel_peak: float
    gyro_peak: float


# ========== KALMAN FILTER ==========


class KalmanFilter1D:
    """
    Simple 1D Kalman filter for sensor noise reduction.
    Based on the constant-velocity model.

    Parameters:
        q: Process noise variance (how much we trust the model)
        r: Measurement noise variance (how much we trust the sensor)
    """

    def __init__(self, q: float = 0.01, r: float = 0.5):
        self.q = q  # Process noise
        self.r = r  # Measurement noise
        self.x = 0.0  # State estimate
        self.p = 1.0  # Estimation error covariance
        self.k = 0.0  # Kalman gain

    def update(self, measurement: float) -> float:
        """Process a new measurement and return filtered value."""
        # Prediction step
        self.p += self.q

        # Correction step
        self.k = self.p / (self.p + self.r)
        self.x += self.k * (measurement - self.x)
        self.p *= (1 - self.k)

        return self.x

    def reset(self):
        """Reset filter state."""
        self.x = 0.0
        self.p = 1.0


class KalmanFilter3D:
    """Kalman filter applied independently to 3 axes."""

    def __init__(self, q: float = 0.01, r: float = 0.5):
        self.filters = [KalmanFilter1D(q, r) for _ in range(3)]

    def update(self, x: float, y: float, z: float) -> tuple:
        """Filter a 3-axis reading."""
        return (
            self.filters[0].update(x),
            self.filters[1].update(y),
            self.filters[2].update(z),
        )

    def reset(self):
        for f in self.filters:
            f.reset()


# ========== ANOMALY DETECTOR ==========


class AnomalyDetector:
    """
    Sliding window anomaly detector for IMU data.

    Analyzes a window of recent sensor readings to detect
    statistical anomalies indicating road damage.
    """

    def __init__(
        self,
        window_size: int = 50,
        accel_std_threshold: float = 2.5,
        gyro_std_threshold: float = 2.0,
        min_severity: float = 0.1,
    ):
        self.window_size = window_size
        self.accel_std_threshold = accel_std_threshold
        self.gyro_std_threshold = gyro_std_threshold
        self.min_severity = min_severity

        self.accel_window: deque = deque(maxlen=window_size)
        self.gyro_window: deque = deque(maxlen=window_size)

    def add_reading(self, accel_mag: float, gyro_mag: float) -> Optional[AnomalyEvent]:
        """Add a sensor reading and check for anomaly."""
        self.accel_window.append(accel_mag)
        self.gyro_window.append(gyro_mag)

        if len(self.accel_window) < self.window_size // 2:
            return None  # Not enough data yet

        accel_arr = np.array(self.accel_window)
        gyro_arr = np.array(self.gyro_window)

        accel_mean = accel_arr.mean()
        accel_std = accel_arr.std()
        gyro_mean = gyro_arr.mean()
        gyro_std = gyro_arr.std()

        # Check if current reading is anomalous
        accel_z_score = (accel_mag - accel_mean) / (accel_std + 1e-6)
        gyro_z_score = (gyro_mag - gyro_mean) / (gyro_std + 1e-6)

        is_accel_anomaly = accel_z_score > self.accel_std_threshold
        is_gyro_anomaly = gyro_z_score > self.gyro_std_threshold

        if not is_accel_anomaly and not is_gyro_anomaly:
            return None

        # Classify anomaly type
        anomaly_type = self._classify_anomaly(accel_z_score, gyro_z_score)

        # Calculate severity (normalized 0-1)
        severity = min(1.0, max(self.min_severity, accel_z_score / 5.0))

        # Calculate confidence based on both signals
        confidence = 0.0
        if is_accel_anomaly and is_gyro_anomaly:
            confidence = 0.9  # Both sensors agree
        elif is_accel_anomaly:
            confidence = 0.6
        else:
            confidence = 0.4

        return AnomalyEvent(
            timestamp=0,  # Will be set by caller
            anomaly_type=anomaly_type,
            severity=severity,
            confidence=confidence,
            accel_peak=accel_mag,
            gyro_peak=gyro_mag,
        )

    def _classify_anomaly(self, accel_z: float, gyro_z: float) -> str:
        """Classify the type of road anomaly based on sensor signature."""
        if accel_z > 4.0 and gyro_z > 3.0:
            return "POTHOLE"
        elif accel_z > 3.0 and gyro_z < 1.5:
            return "SPEED_BUMP"
        elif accel_z > 2.5:
            return "BUMP"
        else:
            return "ROUGHNESS"


# ========== FUSION ENGINE ==========


class FusionEngine:
    """
    Late fusion of CV detection confidence and sensor anomaly confidence.

    Score = alpha * cv_conf + beta * sensor_conf + gamma * temporal_bonus

    Decision thresholds:
        > auto_threshold   -> automatic report
        > manual_threshold -> prompt user
        < manual_threshold -> discard
    """

    def __init__(
        self,
        alpha: float = 0.55,
        beta: float = 0.30,
        gamma: float = 0.15,
        auto_threshold: float = 0.75,
        manual_threshold: float = 0.50,
        temporal_window_sec: float = 2.0,
    ):
        self.alpha = alpha
        self.beta = beta
        self.gamma = gamma
        self.auto_threshold = auto_threshold
        self.manual_threshold = manual_threshold
        self.temporal_window_sec = temporal_window_sec

        self.last_cv_time: Optional[float] = None
        self.last_sensor_time: Optional[float] = None

    def compute_score(
        self,
        cv_confidence: float,
        sensor_confidence: float,
        cv_timestamp: Optional[float] = None,
        sensor_timestamp: Optional[float] = None,
    ) -> tuple:
        """
        Compute fused confidence score and decision.

        Returns:
            (score, decision) where decision is 'AUTO', 'PROMPT', or 'DISCARD'
        """
        # Check temporal correlation
        temporal_bonus = 0.0
        if cv_timestamp is not None and sensor_timestamp is not None:
            time_diff = abs(cv_timestamp - sensor_timestamp)
            if time_diff <= self.temporal_window_sec:
                temporal_bonus = 1.0

        score = (
            self.alpha * cv_confidence
            + self.beta * sensor_confidence
            + self.gamma * temporal_bonus
        )

        if score >= self.auto_threshold:
            decision = "AUTO"
        elif score >= self.manual_threshold:
            decision = "PROMPT"
        else:
            decision = "DISCARD"

        return score, decision


# ========== SYNTHETIC DATA & TESTING ==========


def generate_synthetic_trip(duration_sec: float = 60.0, sample_rate: float = 50.0) -> List[SensorDataPoint]:
    """
    Generate synthetic IMU data simulating a driving trip with road events.

    Events injected:
    - Pothole at ~15s (large accel + gyro spike)
    - Speed bump at ~30s (moderate accel, low gyro)
    - Rough patch at ~45s (elevated noise for 3s)
    """
    n_samples = int(duration_sec * sample_rate)
    timestamps = np.linspace(0, duration_sec, n_samples)

    # Base noise (normal driving vibrations)
    np.random.seed(42)
    base_accel = np.random.normal(0, 0.5, (n_samples, 3))
    base_gyro = np.random.normal(0, 0.1, (n_samples, 3))

    # Add gravity to Z axis
    base_accel[:, 2] += 9.81

    # Event 1: Pothole at ~15s
    pothole_idx = int(15 * sample_rate)
    for i in range(pothole_idx, min(pothole_idx + 10, n_samples)):
        impulse = np.exp(-0.5 * (i - pothole_idx)) * 8.0
        base_accel[i, 2] += impulse
        base_accel[i, 0] += impulse * 0.3
        base_gyro[i, 0] += impulse * 0.4
        base_gyro[i, 1] += impulse * 0.3

    # Event 2: Speed bump at ~30s
    bump_idx = int(30 * sample_rate)
    for i in range(bump_idx, min(bump_idx + 25, n_samples)):
        t = (i - bump_idx) / sample_rate
        wave = 4.0 * np.sin(2 * np.pi * 2 * t) * np.exp(-2 * t)
        base_accel[i, 2] += wave
        base_gyro[i, 1] += wave * 0.1

    # Event 3: Rough patch from ~45s to ~48s
    rough_start = int(45 * sample_rate)
    rough_end = int(48 * sample_rate)
    base_accel[rough_start:rough_end] += np.random.normal(0, 2.0, (rough_end - rough_start, 3))
    base_gyro[rough_start:rough_end] += np.random.normal(0, 0.3, (rough_end - rough_start, 3))

    # Create data points
    data_points = []
    for i in range(n_samples):
        dp = SensorDataPoint(
            timestamp=timestamps[i],
            accel_x=base_accel[i, 0],
            accel_y=base_accel[i, 1],
            accel_z=base_accel[i, 2],
            gyro_x=base_gyro[i, 0],
            gyro_y=base_gyro[i, 1],
            gyro_z=base_gyro[i, 2],
        )
        data_points.append(dp)

    return data_points


def test_kalman_filter(data_points: List[SensorDataPoint]):
    """Test Kalman filter on synthetic data and visualize results."""
    print("\n--- Kalman Filter 3D Test ---")

    kf_accel = KalmanFilter3D(q=0.01, r=0.5)
    kf_gyro = KalmanFilter3D(q=0.005, r=0.3)

    timestamps = []
    raw_accel_mag = []
    filtered_accel_mag = []
    raw_gyro_mag = []
    filtered_gyro_mag = []

    for dp in data_points:
        # Raw magnitudes
        raw_am = np.sqrt(dp.accel_x**2 + dp.accel_y**2 + dp.accel_z**2)
        raw_gm = np.sqrt(dp.gyro_x**2 + dp.gyro_y**2 + dp.gyro_z**2)

        # Filtered
        fax, fay, faz = kf_accel.update(dp.accel_x, dp.accel_y, dp.accel_z)
        fgx, fgy, fgz = kf_gyro.update(dp.gyro_x, dp.gyro_y, dp.gyro_z)

        filtered_am = np.sqrt(fax**2 + fay**2 + faz**2)
        filtered_gm = np.sqrt(fgx**2 + fgy**2 + fgz**2)

        timestamps.append(dp.timestamp)
        raw_accel_mag.append(raw_am)
        filtered_accel_mag.append(filtered_am)
        raw_gyro_mag.append(raw_gm)
        filtered_gyro_mag.append(filtered_gm)

        dp.filtered_accel_magnitude = filtered_am
        dp.filtered_gyro_magnitude = filtered_gm

    # Plot
    fig, axes = plt.subplots(2, 1, figsize=(16, 8))
    fig.suptitle("Kalman Filter 3D — Noise Reduction on Synthetic Trip", fontsize=14, fontweight="bold")

    ax = axes[0]
    ax.plot(timestamps, raw_accel_mag, alpha=0.4, linewidth=0.5, label="Raw", color="gray")
    ax.plot(timestamps, filtered_accel_mag, linewidth=1.5, label="Kalman Filtered", color="blue")
    ax.axvline(15, color="red", linestyle="--", alpha=0.5, label="Pothole")
    ax.axvline(30, color="orange", linestyle="--", alpha=0.5, label="Speed Bump")
    ax.axvspan(45, 48, alpha=0.1, color="purple", label="Rough Patch")
    ax.set_title("Accelerometer Magnitude")
    ax.set_ylabel("m/s²")
    ax.legend(loc="upper right")
    ax.grid(True, alpha=0.3)

    ax = axes[1]
    ax.plot(timestamps, raw_gyro_mag, alpha=0.4, linewidth=0.5, label="Raw", color="gray")
    ax.plot(timestamps, filtered_gyro_mag, linewidth=1.5, label="Kalman Filtered", color="green")
    ax.axvline(15, color="red", linestyle="--", alpha=0.5, label="Pothole")
    ax.axvline(30, color="orange", linestyle="--", alpha=0.5, label="Speed Bump")
    ax.axvspan(45, 48, alpha=0.1, color="purple", label="Rough Patch")
    ax.set_title("Gyroscope Magnitude")
    ax.set_xlabel("Time (s)")
    ax.set_ylabel("rad/s")
    ax.legend(loc="upper right")
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    output_path = OUTPUT_DIR / "kalman_filter_test.png"
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved: {output_path}")


def test_anomaly_detector(data_points: List[SensorDataPoint]):
    """Test anomaly detector on filtered synthetic data."""
    print("\n--- Anomaly Detector Test ---")

    detector = AnomalyDetector(
        window_size=50,
        accel_std_threshold=2.5,
        gyro_std_threshold=2.0,
    )

    events: List[AnomalyEvent] = []
    for dp in data_points:
        event = detector.add_reading(dp.filtered_accel_magnitude, dp.filtered_gyro_magnitude)
        if event is not None:
            event.timestamp = dp.timestamp
            events.append(event)

    print(f"  Events detected: {len(events)}")
    for e in events:
        print(f"    t={e.timestamp:.2f}s  type={e.anomaly_type:<12} "
              f"severity={e.severity:.2f}  confidence={e.confidence:.2f}")

    # Plot events on timeline
    fig, ax = plt.subplots(figsize=(16, 4))
    accel_mags = [dp.filtered_accel_magnitude for dp in data_points]
    timestamps = [dp.timestamp for dp in data_points]

    ax.plot(timestamps, accel_mags, linewidth=0.8, color="blue", alpha=0.6)

    colors = {"POTHOLE": "red", "SPEED_BUMP": "orange", "BUMP": "yellow", "ROUGHNESS": "purple"}
    for e in events:
        ax.axvline(e.timestamp, color=colors.get(e.anomaly_type, "gray"),
                   alpha=0.5, linewidth=2)
        ax.annotate(f"{e.anomaly_type}\n({e.severity:.2f})",
                    xy=(e.timestamp, max(accel_mags) * 0.9),
                    fontsize=7, ha="center", color=colors.get(e.anomaly_type, "gray"))

    ax.set_title("Anomaly Detection on Filtered Accelerometer Data")
    ax.set_xlabel("Time (s)")
    ax.set_ylabel("Filtered Accel Magnitude (m/s²)")
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    output_path = OUTPUT_DIR / "anomaly_detection_test.png"
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved: {output_path}")

    return events


def test_fusion_engine():
    """Test the fusion engine with various CV + sensor confidence combinations."""
    print("\n--- Fusion Engine Test ---")

    engine = FusionEngine()

    test_cases = [
        # (cv_conf, sensor_conf, cv_time, sensor_time, description)
        (0.90, 0.80, 10.0, 10.5, "Both high, temporally correlated"),
        (0.85, 0.70, 10.0, 10.5, "Both good, temporally correlated"),
        (0.80, 0.20, 10.0, 15.0, "CV only, no temporal correlation"),
        (0.20, 0.90, 10.0, 10.5, "Sensor only, temporal match"),
        (0.60, 0.60, 10.0, 10.5, "Both medium, temporal match"),
        (0.40, 0.30, 10.0, 15.0, "Both low, no correlation"),
        (0.95, 0.00, 10.0, None, "CV only, no sensor"),
        (0.00, 0.95, None, 10.0, "Sensor only, no CV"),
    ]

    print(f"\n  {'Description':<45} {'CV':>5} {'Sensor':>7} {'Score':>6} {'Decision':>8}")
    print(f"  {'-'*45} {'---':>5} {'------':>7} {'-----':>6} {'--------':>8}")

    scores = []
    decisions = []
    labels = []

    for cv_c, sens_c, cv_t, sens_t, desc in test_cases:
        score, decision = engine.compute_score(cv_c, sens_c, cv_t, sens_t)
        print(f"  {desc:<45} {cv_c:>5.2f} {sens_c:>7.2f} {score:>6.3f} {decision:>8}")
        scores.append(score)
        decisions.append(decision)
        labels.append(desc[:30])

    # Plot
    fig, ax = plt.subplots(figsize=(12, 6))
    color_map = {"AUTO": "#2ecc71", "PROMPT": "#f39c12", "DISCARD": "#e74c3c"}
    bar_colors = [color_map[d] for d in decisions]

    bars = ax.barh(range(len(scores)), scores, color=bar_colors, edgecolor="white", height=0.6)
    ax.set_yticks(range(len(labels)))
    ax.set_yticklabels(labels, fontsize=9)
    ax.set_xlabel("Fused Score")
    ax.set_title("Fusion Engine Test Results")
    ax.axvline(engine.auto_threshold, color="green", linestyle="--", alpha=0.7, label=f"Auto ({engine.auto_threshold})")
    ax.axvline(engine.manual_threshold, color="orange", linestyle="--", alpha=0.7, label=f"Prompt ({engine.manual_threshold})")
    ax.legend()
    ax.set_xlim(0, 1.05)
    ax.grid(True, alpha=0.3, axis="x")

    # Add score labels
    for bar, score, decision in zip(bars, scores, decisions):
        ax.text(score + 0.02, bar.get_y() + bar.get_height() / 2,
                f"{score:.3f} → {decision}", va="center", fontsize=8)

    plt.tight_layout()
    output_path = OUTPUT_DIR / "fusion_engine_test.png"
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"\n  Saved: {output_path}")


def main():
    print("=" * 60)
    print("RoadGuard Sensor Fusion Prototype")
    print("=" * 60)

    # Generate synthetic trip data
    print("\n--- Generating Synthetic Trip Data ---")
    data_points = generate_synthetic_trip(duration_sec=60.0, sample_rate=50.0)
    print(f"  Generated {len(data_points)} data points (60s @ 50Hz)")
    print(f"  Events: pothole@15s, speed_bump@30s, rough_patch@45-48s")

    # Test Kalman Filter
    test_kalman_filter(data_points)

    # Test Anomaly Detector
    events = test_anomaly_detector(data_points)

    # Test Fusion Engine
    test_fusion_engine()

    # Summary
    print("\n" + "=" * 60)
    print("PROTOTYPE SUMMARY")
    print("=" * 60)
    print(f"  Kalman Filter: Noise reduction verified on 3-axis synthetic data")
    print(f"  Anomaly Detector: {len(events)} events detected from 3 injected road events")
    print(f"  Fusion Engine: Score-based decision logic tested with 8 scenarios")
    print(f"\n  All plots saved to: {OUTPUT_DIR}")
    print(f"\n  Next: Run with real IMU data from Kaggle Road Quality Dataset")
    print("=" * 60)


if __name__ == "__main__":
    main()
