# RoadGuard

[![CI](https://github.com/antoninofoti/roadguard/actions/workflows/ci.yml/badge.svg)](https://github.com/antoninofoti/roadguard/actions/workflows/ci.yml)

> **Multi-Modal Road Damage Detection System** — Android application for crowdsourced road monitoring using Late Fusion of Computer Vision (YOLOv8 TFLite) and Inertial Sensors (Accelerometer + Gyroscope), with Predictive Analytics and an Operator Dashboard.

## Thesis

**Title**: *A Multi-Modal Approach to Road Damage Detection and Predictive Maintenance Using Late Fusion of Computer Vision and Inertial Sensors on Mobile Devices*

**Author**: Antonino Foti
**Degree**: Laurea Magistrale in Engineering in Computer Science
**University**: Sapienza Università di Roma

## Architecture

```
Camera → YOLOv8 TFLite → ┐
                          ├→ Fusion Engine → Auto Report → Firebase → Operator Dashboard
Accel+Gyro → Kalman 3D → ┘                                          → Predictive Analytics
```

**Key formula**: `Score = 0.55 × CV + 0.30 × Sensor + 0.15 × Temporal`

## Federated Learning (FedRoadGuard)

The system includes a decentralized optimization layer that allows individual vehicles to personalize their fusion weights without sharing raw data:

- **Personalization**: Local Grid Search on user feedback improves detection F1-score by **+2.98 pp** on average.
- **Privacy**: Implements **Local Differential Privacy (LDP)** with Gaussian noise (ε=1.0) on gradient/metric uploads.
- **Scalability**: Federated Averaging (FedAvg) simulated via the **Flower** framework, achieving 86% of centralized performance in a non-IID geographic distribution.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| CV Model | YOLOv8n via TensorFlow Lite |
| Signal Processing | Kalman Filter 3D + Z-score Anomaly Detection |
| Backend | Firebase (Auth, Firestore, Storage) |
| Maps | Google Maps SDK + Maps Compose |
| Camera | CameraX |
| Image Processing | OpenCV 4.11 |
| CI/CD | GitHub Actions (test, lint, coverage, APK) |

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport
# Report: app/build/reports/jacoco/jacocoTestReport/html/index.html

# Static analysis
./gradlew detekt
```

## Project Structure

```
com.example.roadguard/
├── sensor/        # Kalman Filter 3D, Anomaly Detector
├── detection/     # Fusion Engine (Late Fusion)
├── analytics/     # Predictive Analytics (Priority, Clustering, Trends)
├── operator/      # Operator Dashboard (RBAC)
├── model/         # Data models (Report, User)
├── repository/    # Firebase data access
├── services/      # Background sensor service
├── ui/            # Camera + live detection
├── view/          # Core screens
├── home/          # User-facing screens
├── auth/          # Authentication
└── navigation/    # Role-based navigation
```

## Testing

- **164 JVM unit tests** covering sensor processing, fusion engine, analytics, integration, and JNI parity checks
- **JaCoCo** code coverage with HTML/XML reports
- **Detekt** static analysis for Kotlin code quality
- **Android Lint** for Android-specific checks

## License

This project is developed as part of a Master's thesis at Sapienza Università di Roma.
