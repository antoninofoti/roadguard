import os
import shutil
from ultralytics import YOLO

def main():
    best_model_path = "ml/runs/roadguard_v1/weights/best.pt"
    if not os.path.exists(best_model_path):
        print(f"Error: {best_model_path} not found. Please run training first.")
        return

    # Load the trained model
    print(f"Loading best model from {best_model_path}...")
    model = YOLO(best_model_path)
    
    # Export to TFLite INT8
    # int8=True applies INT8 quantization to reduce the size to < 10MB
    print("Exporting model to TFLite INT8...")
    # Passing data="ml/pothole_yolo.yaml" is often required for INT8 calibration in ultralytics
    model.export(format="tflite", int8=True, data="ml/pothole_yolo.yaml")
    
    # ultralytics saves the exported file as best_saved_model/best_int8.tflite or best_int8.tflite
    weights_dir = os.path.dirname(best_model_path)
    tflite_model_path = best_model_path.replace(".pt", "_int8.tflite")
    if not os.path.exists(tflite_model_path):
        tflite_model_path = best_model_path.replace(".pt", "_saved_model/best_int8.tflite")
    if not os.path.exists(tflite_model_path):
        # Fallback to look for any .tflite
        for file in os.listdir(weights_dir):
            if file.endswith(".tflite"):
                tflite_model_path = os.path.join(weights_dir, file)
                break

    if not os.path.exists(tflite_model_path):
        print("Error: Could not find the exported .tflite file. Export might have failed.")
        return

    # Target path in Android app
    android_assets_dir = "app/src/main/assets"
    
    if not os.path.exists(android_assets_dir):
        os.makedirs(android_assets_dir, exist_ok=True)
        
    target_path = os.path.join(android_assets_dir, "roadguard_model.tflite")
    
    # Copy the file
    shutil.copy(tflite_model_path, target_path)
    print(f"Model successfully exported and copied to: {target_path}")
    
    # Verify file size
    file_size_mb = os.path.getsize(target_path) / (1024 * 1024)
    print(f"TFLite Model Size: {file_size_mb:.2f} MB")
    if file_size_mb < 10.0:
        print("Size check PASSED (< 10 MB).")
    else:
        print("Size check FAILED (>= 10 MB). Mobile deployment might be affected.")

if __name__ == "__main__":
    main()
