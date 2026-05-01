#!/usr/bin/env bash
# Script to start the entire RoadGuard (Demo) local testing environment.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "==============================================="
echo "Starting RoadGuard Local Testing Environment"
echo "==============================================="

# Dependency check
if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed or not in PATH."
    exit 1
fi
if ! command -v npx &> /dev/null; then
    echo "ERROR: Node.js (npx) is not installed."
    exit 1
fi

# 1. Start Firebase Emulator
echo "[1/3] Starting Firebase Emulator (Firestore & Auth)..."
npx -y firebase-tools emulators:start --project roadguard-demo --only auth,firestore > /tmp/firebase_emulator.log 2>&1 &
FIREBASE_PID=$!

# Automatic shutdown function
cleanup() {
    echo -e "\nShutting down services..."
    echo "Stopping Firebase Emulator (PID: $FIREBASE_PID)..."
    kill $FIREBASE_PID 2>/dev/null || true
    echo "Stopping Docker Compose..."
    docker compose down
    echo "Environment shutdown complete."
    exit 0
}
trap cleanup SIGINT SIGTERM

# Wait for Firebase to be online
echo "Waiting for emulators to start (approx. 10 seconds)..."
sleep 10

# 2. Start Web Portal and API (Docker Compose)
echo "[2/3] Starting Docker Compose (Web Portal & Analytics API)..."
if [ ! -f .env.docker ]; then
    echo "Copying .env.docker.example to .env.docker..."
    cp .env.docker.example .env.docker
fi

# Copia l'ambiente in web-portal per la fase di build (Vite e validate script)
cp .env.docker web-portal/.env.production

# Usa --env-file in modo che docker-compose risolva le variabili ${VITE_...}
docker compose --env-file .env.docker up --build -d

echo "Waiting for container boot sequence to complete..."
sleep 5

# 3. Data Mocking
echo "[3/3] Injecting mock data into the local database..."

# Send mock reports via Firestore Emulator REST API
curl -sS -X POST "http://127.0.0.1:8080/v1/projects/roadguard-demo/databases/(default)/documents/reports" \
    -H "Content-Type: application/json" \
    -d '{
          "fields": {
            "latitude": { "doubleValue": 45.4642 },
            "longitude": { "doubleValue": 9.1900 },
            "fusedScore": { "doubleValue": 0.94 },
            "damageType": { "stringValue": "pothole" },
            "status": { "stringValue": "pending" },
            "timestampMs": { "integerValue": "1714000000000" }
          }
        }' > /dev/null || true

curl -sS -X POST "http://127.0.0.1:8080/v1/projects/roadguard-demo/databases/(default)/documents/reports" \
    -H "Content-Type: application/json" \
    -d '{
          "fields": {
            "latitude": { "doubleValue": 41.9028 },
            "longitude": { "doubleValue": 12.4964 },
            "fusedScore": { "doubleValue": 0.82 },
            "damageType": { "stringValue": "bump" },
            "status": { "stringValue": "pending" },
            "timestampMs": { "integerValue": "1714000500000" }
          }
        }' > /dev/null || true

echo "==============================================="
echo "Environment is ready and running."
echo "  Web Portal (Dashboard): http://localhost:3000"
echo "  Analytics API (Swagger): http://localhost:8000/docs"
echo "  Firebase UI (Database): http://localhost:4000"
echo ""
echo "For the Android app: run via Android Studio."
echo "(Ensure Firestore points to 10.0.2.2:8080 in code if using Android emulator)"
echo ""
echo "Press Ctrl+C to shut down all services."
echo "==============================================="

# Keep process alive to intercept Ctrl+C
wait $FIREBASE_PID
