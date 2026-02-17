#!/usr/bin/env python3
"""
NHA12D Crack Detection Dataset Explorer

Analyzes the NHA12D dataset (80 pavement images: 40 concrete + 40 asphalt).
Reference: Huang et al., EC3 2022, DOI: 10.35490/EC3.2022.160

Expected structure in data/nha12d/:
    - images/ — pavement images
    - masks/ or labels/ — pixel-level crack annotations

Usage:
    python3 explore_nha12d.py
"""

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
DATA_DIR = BASE_DIR / "data" / "nha12d"
OUTPUT_DIR = BASE_DIR / "output" / "plots"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


def discover_files(data_dir: Path) -> dict:
    """Discover images and masks in the dataset."""
    image_ext = {".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff"}
    files = {"images": [], "masks": []}

    if not data_dir.exists():
        print(f"ERROR: {data_dir} does not exist. Run setup_datasets.py first.")
        sys.exit(1)

    for f in sorted(data_dir.rglob("*")):
        if not f.is_file() or f.suffix.lower() not in image_ext:
            continue
        # Heuristic: masks often in 'mask', 'label', 'gt', 'annotation' directories
        parent_lower = f.parent.name.lower()
        if any(k in parent_lower for k in ["mask", "label", "gt", "annot", "ground"]):
            files["masks"].append(f)
        else:
            files["images"].append(f)

    # If no masks found separately, check if files have mask-like naming
    if not files["masks"]:
        all_imgs = files["images"][:]
        files["images"] = [f for f in all_imgs if "mask" not in f.stem.lower()]
        files["masks"] = [f for f in all_imgs if "mask" in f.stem.lower()]

    print(f"Found: {len(files['images'])} images, {len(files['masks'])} masks")
    return files


def analyze_images(image_files: list) -> dict:
    """Analyze image properties."""
    stats = {"widths": [], "heights": [], "channels": [], "sizes_kb": []}

    for img_path in image_files:
        try:
            with Image.open(img_path) as img:
                w, h = img.size
                stats["widths"].append(w)
                stats["heights"].append(h)
                stats["channels"].append(len(img.getbands()))
                stats["sizes_kb"].append(img_path.stat().st_size / 1024)
        except Exception as e:
            print(f"  Warning: {img_path.name}: {e}")

    return {k: np.array(v) for k, v in stats.items()}


def analyze_masks(mask_files: list) -> dict:
    """Analyze crack mask properties."""
    crack_ratios = []
    mask_values = Counter()

    for mask_path in mask_files:
        try:
            with Image.open(mask_path) as mask:
                arr = np.array(mask)
                if arr.ndim == 3:
                    arr = arr[:, :, 0]  # Take first channel
                total_pixels = arr.size
                # Assume crack pixels are non-zero (or white=255)
                crack_pixels = np.count_nonzero(arr)
                crack_ratio = crack_pixels / total_pixels
                crack_ratios.append(crack_ratio)

                unique_vals = np.unique(arr)
                for v in unique_vals:
                    mask_values[int(v)] += 1
        except Exception as e:
            print(f"  Warning: {mask_path.name}: {e}")

    return {
        "crack_ratios": np.array(crack_ratios),
        "unique_values": dict(mask_values),
    }


def plot_analysis(img_stats: dict, mask_stats: dict, n_images: int, n_masks: int):
    """Generate analysis plots."""
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle("NHA12D Crack Detection Dataset Analysis", fontsize=16, fontweight="bold")

    # 1. Image resolution scatter
    ax = axes[0, 0]
    if len(img_stats["widths"]) > 0:
        ax.scatter(img_stats["widths"], img_stats["heights"], alpha=0.6, c="teal", s=30)
        ax.set_title(f"Image Resolutions (n={n_images})")
        ax.set_xlabel("Width (px)")
        ax.set_ylabel("Height (px)")
    else:
        ax.text(0.5, 0.5, "No images", ha="center", va="center")

    # 2. File size distribution
    ax = axes[0, 1]
    if len(img_stats["sizes_kb"]) > 0:
        ax.hist(img_stats["sizes_kb"], bins=20, color="steelblue", edgecolor="white", alpha=0.8)
        ax.set_title(f"Image File Size Distribution")
        ax.set_xlabel("Size (KB)")
        ax.set_ylabel("Count")
        ax.axvline(np.mean(img_stats["sizes_kb"]), color="red", linestyle="--",
                    label=f"Mean: {np.mean(img_stats['sizes_kb']):.0f} KB")
        ax.legend()

    # 3. Crack coverage ratio
    ax = axes[1, 0]
    if len(mask_stats.get("crack_ratios", [])) > 0:
        ax.hist(mask_stats["crack_ratios"] * 100, bins=20, color="coral", edgecolor="white", alpha=0.8)
        ax.set_title("Crack Coverage per Image")
        ax.set_xlabel("Crack Area (%)")
        ax.set_ylabel("Count")
        mean_ratio = np.mean(mask_stats["crack_ratios"]) * 100
        ax.axvline(mean_ratio, color="red", linestyle="--", label=f"Mean: {mean_ratio:.2f}%")
        ax.legend()
    else:
        ax.text(0.5, 0.5, "No masks found", ha="center", va="center")
        ax.set_title("Crack Coverage")

    # 4. Sample visualization grid
    ax = axes[1, 1]
    ax.text(0.5, 0.5,
            f"Dataset Summary\n\n"
            f"Images: {n_images}\n"
            f"Masks: {n_masks}\n"
            f"Expected: 80 images\n"
            f"(40 concrete + 40 asphalt)",
            ha="center", va="center", fontsize=14,
            bbox=dict(boxstyle="round,pad=0.5", facecolor="lightyellow"))
    ax.set_title("Dataset Info")
    ax.axis("off")

    plt.tight_layout()
    output_path = OUTPUT_DIR / "nha12d_analysis.png"
    plt.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"  Saved: {output_path}")


def main():
    print("=" * 60)
    print("NHA12D Crack Detection Dataset Explorer")
    print("=" * 60)

    files = discover_files(DATA_DIR)

    if not files["images"] and not files["masks"]:
        print("\nNo data found. Download the dataset first.")
        print("URL: https://github.com/ZheningHuang/NHA12D-Crack-Detection-Dataset-and-Comparison-Study")
        print(f"Place in: {DATA_DIR}")
        return

    print("\n--- Image Analysis ---")
    img_stats = analyze_images(files["images"])

    if img_stats["widths"].size > 0:
        print(f"  Resolution range: {int(img_stats['widths'].min())}×{int(img_stats['heights'].min())} "
              f"to {int(img_stats['widths'].max())}×{int(img_stats['heights'].max())}")
        print(f"  Avg file size: {img_stats['sizes_kb'].mean():.1f} KB")

    print("\n--- Mask Analysis ---")
    mask_stats = analyze_masks(files["masks"])

    if mask_stats["crack_ratios"].size > 0:
        print(f"  Avg crack coverage: {mask_stats['crack_ratios'].mean()*100:.2f}%")
        print(f"  Min/Max coverage: {mask_stats['crack_ratios'].min()*100:.2f}% / "
              f"{mask_stats['crack_ratios'].max()*100:.2f}%")
        print(f"  Unique mask values: {mask_stats['unique_values']}")

    print("\n--- Generating Plots ---")
    plot_analysis(img_stats, mask_stats, len(files["images"]), len(files["masks"]))

    print(f"\nPlots saved to: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
