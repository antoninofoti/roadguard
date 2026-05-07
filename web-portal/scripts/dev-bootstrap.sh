#!/usr/bin/env bash
set -euo pipefail

# Dev bootstrap: start Firebase emulators, seed Firestore, start Vite dev server
# Usage: ./scripts/dev-bootstrap.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "Starting Firebase emulators (auth + firestore)..."
# Start emulators in background
npx -y firebase-tools --config ../firebase.json emulators:start --project roadguard-demo --only auth,firestore &
EMULATORS_PID=$!

# Ensure we kill emulators on exit
trap 'echo "Stopping emulators..."; kill $EMULATORS_PID 2>/dev/null || true' EXIT

# Wait a little for emulators to come up
sleep 3

echo "Seeding Firestore emulator with test data..."
node scripts/seed-firestore-test-data.mjs

echo "Starting Vite dev server..."
npm run dev

# When dev server exits, trap will kill emulators
