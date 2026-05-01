# RoadGuard Dataset Analysis

This directory contains Python scripts for exploring and analyzing the datasets used in the RoadGuard thesis.

## Setup

```bash
# Create virtual environment (recommended on immutable Fedora/Aurora)
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Scripts

| Script | Description |
|--------|-------------|
| `setup_datasets.py` | Downloads and organizes all datasets |
| `explore_road_quality.py` | Analyzes the Kaggle Road Quality Dataset (IMU + GPS + Camera) |
| `explore_pothole_yolo.py` | Analyzes the Figshare Pothole YOLO dataset |
| `explore_nha12d.py` | Analyzes the NHA12D Crack Detection dataset |
| `sensor_fusion_prototype.py` | Prototype of Kalman filter + anomaly detection on IMU data |

## Datasets

Download datasets manually to the `data/` subdirectory:

```
analysis/
├── data/
│   ├── road_quality/           # Kaggle Road Quality Dataset
│   ├── pothole_yolo/           # Figshare Potholes with YOLO annotations
│   ├── mobilite_net/           # Figshare MobiLiteNet distress dataset
│   └── nha12d/                 # NHA12D Crack Detection
├── output/                     # Generated plots and statistics
└── *.py                        # Analysis scripts
```
