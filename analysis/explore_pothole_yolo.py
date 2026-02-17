#!/usr/bin/env python3
"""
Pothole YOLO Dataset Explorer (Figshare)

Analyzes the pothole dataset with YOLO-format annotations.
Produces statistics on bounding box distributions, aspect ratios, and class balance.

Expected structure in data/pothole_yolo/:
    - images/ — pothole images
    - labels/ — YOLO format annotation files (.txt)

YOLO annotation format per line:
    class_id center_x center_y width height  (all normalized 0-1)

Usage:
    python3 explore_pothole_yolo.py
"""

import os
import sys
from pathlib import Path
from collections import Counter

import numpy as np
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns

try:
    from PIL import Image
except ImportError:
    print("Pillow not installed. Run: pip install Pillow")
    sys.exit(1)

BASE_DIR = Path(__file__).parent
DATA_DIR = BASE_DIR / "data" / "pothole_yolo"
OUTPUT_DIR = BASE_DIR / "output" / "plots"

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def find_images_and_labels(data_dir: Path) -> tuple:
    """Find image files and their corresponding YOLO label files."""
    image_extensions = {".jpg", ".jpeg", ".png", ".bmp"}
    images = []
    labels = []

    # Try common directory structures
    image_dirs = [
        data_dir / "images",
        data_dir / "train" / "images",
        data_dir / "Images",
        data_dir,
    ]
    label_dirs = [
        data_dir / "labels",
        data_dir / "train" / "labels",
        data_dir / "Labels",
        data_dir,
    ]

    found_images = []
    for img_dir in image_dirs:
        if img_dir.exists():
            for f in sorted(img_dir.rglob("*")):
                if f.is_file() and f.suffix.lower() in image_extensions:
                    found_images.append(f)
            if found_images:
                break

    found_labels = []
    for lbl_dir in label_dirs:
        if lbl_dir.exists():
            for f in sorted(lbl_dir.rglob("*.txt")):
                if f.is_file() and f.name != "classes.txt":
                    found_labels.append(f)
            if found_labels:
                break

    print(f"Found: {len(found_images)} images, {len(found_labels)} label files")
    return found_images, found_labels


def parse_yolo_labels(label_files: list) -> dict:
    """Parse YOLO format label files and extract bounding box statistics."""
    all_boxes = []
    class_counts = Counter()
    boxes_per_image = []

    for lbl_file in label_files:
        image_boxes = []
        try:
            with open(lbl_file, "r") as f:
                lines = f.readlines()

            for line in lines:
                parts = line.strip().split()
                if len(parts) >= 5:
                    class_id = int(parts[0])
                    cx, cy, w, h = float(parts[1]), float(parts[2]), float(parts[3]), float(parts[4])
                    all_boxes.append({
                        "class_id": class_id,
                        "center_x": cx,
                        "center_y": cy,
                        "width": w,
                        "height": h,
                        "aspect_ratio": w / h if h > 0 else 0,
                        "area": w * h,
                    })
                    class_counts[class_id] += 1
                    image_boxes.append(class_id)

            boxes_per_image.append(len(image_boxes))
        except Exception as e:
            print(f"  Warning: Could not parse {lbl_file.name}: {e}")

    return {
        "boxes": all_boxes,
        "class_counts": class_counts,
        "boxes_per_image": boxes_per_image,
    }


def analyze_image_properties(image_files: list, max_sample: int = 200) -> dict:
    """Analyze image dimensions, aspect ratios, and file sizes."""
    widths, heights, sizes = [], [], []

    sample = image_files[:max_sample]
    for img_path in sample:
        try:
            with Image.open(img_path) as img:
                w, h = img.size
                widths.append(w)
                heights.append(h)
                sizes.append(img_path.stat().st_size / 1024)  # KB
        except Exception:
            continue

    return {
        "widths": np.array(widths),
        "heights": np.array(heights),
        "sizes_kb": np.array(sizes),
    }


def plot_dataset_statistics(box_data: dict, img_data: dict):
    """Generate comprehensive dataset statistics plots."""

    fig, axes = plt.subplots(2, 3, figsize=(18, 10))
    fig.suptitle("Pothole YOLO Dataset Analysis", fontsize=16, fontweight="bold")

    # 1. Class distribution
    ax = axes[0, 0]
    if box_data["class_counts"]:
        classes = sorted(box_data["class_counts"].keys())
        counts = [box_data["class_counts"][c] for c in classes]
        ax.bar([f"Class {c}" for c in classes], counts, color=sns.color_palette("Set2"))
        ax.set_title("Class Distribution")
        ax.set_ylabel("Count")
    else:
        ax.text(0.5, 0.5, "No annotations", ha="center", va="center")
        ax.set_title("Class Distribution")

    # 2. Boxes per image
    ax = axes[0, 1]
    if box_data["boxes_per_image"]:
        ax.hist(box_data["boxes_per_image"], bins=range(max(box_data["boxes_per_image"]) + 2),
                color="steelblue", edgecolor="white", alpha=0.8)
        ax.set_title(f"Bounding Boxes per Image (avg: {np.mean(box_data['boxes_per_image']):.1f})")
        ax.set_xlabel("Number of Boxes")
        ax.set_ylabel("Frequency")
    else:
        ax.text(0.5, 0.5, "No data", ha="center", va="center")

    # 3. Box size distribution
    ax = axes[0, 2]
    if box_data["boxes"]:
        areas = [b["area"] for b in box_data["boxes"]]
        ax.hist(areas, bins=50, color="coral", edgecolor="white", alpha=0.8)
        ax.set_title(f"Box Area Distribution (normalized)")
        ax.set_xlabel("Area (w×h, normalized)")
        ax.set_ylabel("Frequency")
    else:
        ax.text(0.5, 0.5, "No data", ha="center", va="center")

    # 4. Box aspect ratio
    ax = axes[1, 0]
    if box_data["boxes"]:
        ratios = [b["aspect_ratio"] for b in box_data["boxes"]]
        ax.hist(ratios, bins=50, color="mediumpurple", edgecolor="white", alpha=0.8)
        ax.axvline(x=1.0, color="red", linestyle="--", label="Square (1:1)")
        ax.set_title("Box Aspect Ratio (W/H)")
        ax.set_xlabel("Aspect Ratio")
        ax.set_ylabel("Frequency")
        ax.legend()
    else:
        ax.text(0.5, 0.5, "No data", ha="center", va="center")

    # 5. Image resolution distribution
    ax = axes[1, 1]
    if len(img_data.get("widths", [])) > 0:
        ax.scatter(img_data["widths"], img_data["heights"], alpha=0.4, s=10, c="teal")
        ax.set_title(f"Image Resolutions (n={len(img_data['widths'])})")
        ax.set_xlabel("Width (px)")
        ax.set_ylabel("Height (px)")
        ax.set_aspect("equal")
    else:
        ax.text(0.5, 0.5, "No images", ha="center", va="center")

    # 6. Box center heatmap
    ax = axes[1, 2]
    if box_data["boxes"]:
        cx = [b["center_x"] for b in box_data["boxes"]]
        cy = [b["center_y"] for b in box_data["boxes"]]
        ax.hist2d(cx, cy, bins=20, cmap="YlOrRd")
        ax.set_title("Box Center Heatmap")
        ax.set_xlabel("Center X (normalized)")
        ax.set_ylabel("Center Y (normalized)")
        ax.invert_yaxis()
    else:
        ax.text(0.5, 0.5, "No data", ha="center", va="center")

    plt.tight_layout()
    output_path = OUTPUT_DIR / "pothole_yolo_analysis.png"
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved: {output_path}")


def print_summary(box_data: dict, img_data: dict, n_images: int, n_labels: int):
    """Print a text summary of the dataset analysis."""
    print("\n" + "=" * 60)
    print("DATASET SUMMARY")
    print("=" * 60)
    print(f"  Images: {n_images}")
    print(f"  Label files: {n_labels}")
    print(f"  Total bounding boxes: {len(box_data['boxes'])}")

    if box_data["boxes"]:
        print(f"  Avg boxes per image: {np.mean(box_data['boxes_per_image']):.2f}")
        print(f"  Max boxes per image: {max(box_data['boxes_per_image'])}")
        print(f"  Classes: {dict(box_data['class_counts'])}")

        areas = [b["area"] for b in box_data["boxes"]]
        print(f"  Box area: mean={np.mean(areas):.4f}, std={np.std(areas):.4f}")
        print(f"  Box area: min={np.min(areas):.4f}, max={np.max(areas):.4f}")

    if len(img_data.get("widths", [])) > 0:
        print(f"  Image widths: {int(np.min(img_data['widths']))}-{int(np.max(img_data['widths']))} px")
        print(f"  Image heights: {int(np.min(img_data['heights']))}-{int(np.max(img_data['heights']))} px")
        print(f"  Avg file size: {np.mean(img_data['sizes_kb']):.1f} KB")


def main():
    print("=" * 60)
    print("Pothole YOLO Dataset Explorer")
    print("=" * 60)

    if not DATA_DIR.exists():
        print(f"\nERROR: {DATA_DIR} does not exist.")
        print("Run setup_datasets.py first, then download the dataset.")
        return

    images, labels = find_images_and_labels(DATA_DIR)

    if not images and not labels:
        print("\nNo data found. Download the dataset first.")
        print(f"URL: https://figshare.com/articles/figure/Potholes_dataset_with_YOLO_annotations/21214400/3")
        print(f"Place in: {DATA_DIR}")
        return

    print("\n--- Parsing YOLO Annotations ---")
    box_data = parse_yolo_labels(labels)

    print("\n--- Analyzing Image Properties ---")
    img_data = analyze_image_properties(images)

    print("\n--- Generating Plots ---")
    plot_dataset_statistics(box_data, img_data)

    print_summary(box_data, img_data, len(images), len(labels))

    print(f"\nPlots saved to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
