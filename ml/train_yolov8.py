import os
import urllib.request
import zipfile
import shutil
from ultralytics import YOLO

def download_dataset():
    data_dir = "data"
    dataset_zip = os.path.join(data_dir, "pothole_dataset.zip")
    extract_dir = os.path.join(data_dir, "pothole_dataset")
    
    if not os.path.exists(data_dir):
        os.makedirs(data_dir)
        
    # Check if dataset already extracted
    if os.path.exists(os.path.join(extract_dir, "train")) or os.path.exists(os.path.join(extract_dir, "images", "train")):
        print("Dataset already downloaded and extracted.")
        return

    # User's figshare link: https://figshare.com/articles/figure/Potholes_dataset_with_YOLO_annotations/21214400/3
    # As figshare direct downloads might require scraping, we will instruct the user or use a dummy placeholder if we can't download.
    # Note: In a real automated pipeline, we would have the exact direct URL (e.g. from the 'Download all' button).
    print("Warning: Auto-download from Figshare HTML page requires the direct zip link.")
    print("Please ensure the dataset is extracted to data/pothole_dataset/")
    print("It should contain images/train and images/val directories.")
    
    # Let's create dummy directories so the script doesn't instantly crash if the user hasn't downloaded it yet,
    # but the user should provide the actual data.
    os.makedirs(os.path.join(extract_dir, "images", "train"), exist_ok=True)
    os.makedirs(os.path.join(extract_dir, "images", "val"), exist_ok=True)
    os.makedirs(os.path.join(extract_dir, "labels", "train"), exist_ok=True)
    os.makedirs(os.path.join(extract_dir, "labels", "val"), exist_ok=True)

def create_yaml():
    yaml_content = """
path: ../data/pothole_dataset # dataset root dir
train: images/train # train images (relative to 'path')
val: images/val # val images (relative to 'path')

# Classes
names:
  0: pothole
"""
    os.makedirs("ml", exist_ok=True)
    with open("ml/pothole_yolo.yaml", "w") as f:
        f.write(yaml_content.strip())
    print("Created ml/pothole_yolo.yaml")

def main():
    download_dataset()
    create_yaml()
    
    # Load YOLOv8n model
    model = YOLO("yolov8n.pt")
    
    # Train the model
    # Task 1 requirements: 50 epochs, imgsz=640, batch=16, lr0=0.001, augment=True, mosaic=True, patience=10
    print("Starting YOLOv8n training on pothole dataset...")
    results = model.train(
        data="ml/pothole_yolo.yaml",
        epochs=50,
        imgsz=640,
        batch=16,
        lr0=0.001,
        augment=True,
        mosaic=True,
        patience=10,
        project="ml/runs",
        name="roadguard_v1",
        exist_ok=True
    )
    
    # Print final metrics
    print("\n--- Training Finished ---")
    if hasattr(results, 'box'):
        print(f"mAP@0.5: {results.box.map50:.4f}")
        print(f"Precision: {results.box.mp:.4f}")
        print(f"Recall: {results.box.mr:.4f}")
    else:
        print("Training completed. Metrics are available in ml/runs/roadguard_v1/")

if __name__ == "__main__":
    main()
