#!/usr/bin/env python3
"""
Dataset Setup and Download Helper for RoadGuard Thesis

This script helps organize and validate the datasets used in the project.
Datasets must be downloaded manually and placed in the appropriate directories.

Usage:
    python3 setup_datasets.py
"""

import os
import sys
from pathlib import Path

# Base directories
BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data"
OUTPUT_DIR = BASE_DIR / "output"

DATASETS = {
    "road_quality": {
        "name": "Road Quality Dataset (Kaggle)",
        "url": "https://www.kaggle.com/datasets/nickkotarelas/road-quality-dataset",
        "description": "Synchronized IMU, GPS and Camera images from urban driving",
        "expected_contents": ["*.csv", "*.jpg", "*.png"],
        "modalities": ["IMU (accelerometer + gyroscope)", "GPS coordinates", "Camera images"],
        "use_case": "Sensor fusion calibration and threshold tuning",
    },
    "pothole_yolo": {
        "name": "Potholes Dataset with YOLO Annotations (Figshare)",
        "url": "https://figshare.com/articles/figure/Potholes_dataset_with_YOLO_annotations/21214400/3",
        "description": "Pothole images with YOLO-format bounding box annotations",
        "expected_contents": ["images/", "labels/", "*.txt"],
        "modalities": ["Images", "YOLO bbox annotations"],
        "use_case": "Pothole detection model validation",
    },
    "mobilite_net": {
        "name": "MobiLiteNet Road Distress Dataset (Figshare)",
        "url": "https://figshare.com/articles/dataset/Raw_Data_for_A_Lightweight_Deep_Learning_for_Real-Time_Road_Distress_Detection_on_Mobile_Devices/28404875/2",
        "description": "Multi-class road distress images from Europe and Asia",
        "expected_contents": ["*.jpg", "*.png", "*.xml", "*.txt"],
        "modalities": ["Multi-class distress images", "Annotations"],
        "use_case": "Model training and fine-tuning for multi-class detection",
    },
    "nha12d": {
        "name": "NHA12D Crack Detection Dataset (GitHub)",
        "url": "https://github.com/ZheningHuang/NHA12D-Crack-Detection-Dataset-and-Comparison-Study",
        "description": "80 pavement images (40 concrete + 40 asphalt) from UK A12 network",
        "expected_contents": ["*.png", "*.jpg", "masks/"],
        "modalities": ["Pavement images", "Pixel-level crack masks"],
        "use_case": "Crack detection evaluation and domain adaptation",
    },
}


def create_directory_structure():
    """Create the required directory structure for datasets and output."""
    dirs_to_create = [
        DATA_DIR,
        OUTPUT_DIR,
        OUTPUT_DIR / "plots",
        OUTPUT_DIR / "statistics",
    ]

    for dataset_key in DATASETS:
        dirs_to_create.append(DATA_DIR / dataset_key)

    for d in dirs_to_create:
        d.mkdir(parents=True, exist_ok=True)
        print(f"  [OK] {d.relative_to(BASE_DIR)}")


def check_dataset_status():
    """Check which datasets have been downloaded."""
    print("\n" + "=" * 60)
    print("DATASET STATUS")
    print("=" * 60)

    all_ready = True
    for key, info in DATASETS.items():
        dataset_dir = DATA_DIR / key
        if dataset_dir.exists() and any(dataset_dir.iterdir()):
            file_count = sum(1 for _ in dataset_dir.rglob("*") if _.is_file())
            total_size_mb = sum(
                f.stat().st_size for f in dataset_dir.rglob("*") if f.is_file()
            ) / (1024 * 1024)
            print(f"\n  [READY] {info['name']}")
            print(f"          Files: {file_count}, Size: {total_size_mb:.1f} MB")
            print(f"          Path: {dataset_dir}")
        else:
            all_ready = False
            print(f"\n  [MISSING] {info['name']}")
            print(f"            Download from: {info['url']}")
            print(f"            Place files in: {dataset_dir}")
            print(f"            Description: {info['description']}")

    return all_ready


def print_download_instructions():
    """Print detailed download instructions for all datasets."""
    print("\n" + "=" * 60)
    print("DOWNLOAD INSTRUCTIONS")
    print("=" * 60)

    for key, info in DATASETS.items():
        dataset_dir = DATA_DIR / key
        if not dataset_dir.exists() or not any(dataset_dir.iterdir()):
            print(f"\n--- {info['name']} ---")
            print(f"  URL: {info['url']}")
            print(f"  Modalities: {', '.join(info['modalities'])}")
            print(f"  Use case: {info['use_case']}")
            print(f"  Target directory: {dataset_dir}")
            print()


def main():
    print("RoadGuard Dataset Setup")
    print("=" * 60)

    print("\n1. Creating directory structure...")
    create_directory_structure()

    all_ready = check_dataset_status()

    if not all_ready:
        print_download_instructions()
        print("\n" + "=" * 60)
        print("After downloading, run this script again to verify.")
        print("Then run the exploration scripts:")
        print("  python3 explore_road_quality.py")
        print("  python3 explore_pothole_yolo.py")
        print("  python3 explore_nha12d.py")
        print("  python3 sensor_fusion_prototype.py")
    else:
        print("\n\nAll datasets are ready! You can now run the exploration scripts.")


if __name__ == "__main__":
    main()
