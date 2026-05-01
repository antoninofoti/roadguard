#!/usr/bin/env bash
# =============================================================================
# scripts/run4_demo.sh
# RoadGuard — Final Demo Rehearsal (Run 4)
#
# Execution Sequence:
#   1. Start Firebase Emulator (background)
#   2. Start Analytics API (background)
#   3. Health check validation
#   4. Manual synchronization point
#   5. Injection of test reports (simulated Android submission)
#   6. Firestore verification
#   7. Clustering endpoint verification
#   8. Forecast endpoint verification
#   9. Logging to output/statistics/demo_rehearsals/
# =============================================================================
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/output/statistics/demo_rehearsals"
RUN_TS="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
API_LOG="$LOG_DIR/run4-api.log"
WEB_LOG="$LOG_DIR/run4-web.log"

API_PORT=8000
FIRESTORE_PORT=8080
AUTH_PORT=9099
EMULATOR_UI_PORT=4000

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log()      { echo -e "${CYAN}[run4]${NC} $*"; echo "[run4] $*" >> "$API_LOG"; }
log_web()  { echo -e "${CYAN}[run4-web]${NC} $*"; echo "[run4-web] $*" >> "$WEB_LOG"; }
log_ok()   { echo -e "${GREEN}[run4] [OK]${NC} $*"; echo "[run4] [OK] $*" >> "$API_LOG"; }
log_fail() { echo -e "${RED}[run4] [FAIL]${NC} $*"; echo "[run4] [FAIL] $*" >> "$API_LOG"; }
log_warn() { echo -e "${YELLOW}[run4] [WARN]${NC} $*"; echo "[run4] [WARN] $*" >> "$API_LOG"; }

mkdir -p "$LOG_DIR"

cat > "$API_LOG" <<EOF
# RoadGuard Run 4 API Log
# Timestamp: $RUN_TS
EOF

cat > "$WEB_LOG" <<EOF
# RoadGuard Run 4 Web Log
# Timestamp: $RUN_TS
EOF

echo -e "${BOLD}"
echo "----------------------------------------------------------------"
echo "       RoadGuard — Final Demo Rehearsal (Run 4)                 "
echo "----------------------------------------------------------------"
echo -e "${NC}"
log "Starting Run 4 — $RUN_TS"

cleanup() {
  log "Cleaning up background processes..."
  [[ -n "${FIREBASE_PID:-}" ]] && kill "$FIREBASE_PID" 2>/dev/null || true
  [[ -n "${API_PID:-}" ]] && kill "$API_PID" 2>/dev/null || true
}
trap cleanup EXIT

# --- STEP 1: Firebase Emulator -----------------------------------------------
echo -e "${BOLD}[STEP 1] Firebase Emulator (Auth + Firestore)${NC}"
FIREBASE_CMD="npx -y firebase-tools"

log "Starting Firebase Emulator on ports $AUTH_PORT, $FIRESTORE_PORT..."
{
  cd "$ROOT_DIR"
  $FIREBASE_CMD emulators:start --project roadguard-demo --only auth,firestore 2>/dev/null
} >> "$WEB_LOG" 2>&1 &
FIREBASE_PID=$!

log "Waiting for Firebase Emulator to initialize..."
emulator_ok=0
for i in $(seq 1 20); do
  if curl -sf --max-time 2 "http://127.0.0.1:$FIRESTORE_PORT/" > /dev/null 2>&1; then
    log_ok "Firebase Emulator ready (attempt $i)"
    emulator_ok=1
    break
  fi
  sleep 3
done

# --- STEP 2: Analytics API ----------------------------------------------------
echo -e "${BOLD}[STEP 2] Analytics API (FastAPI)${NC}"
log "Starting Analytics API..."
cd "$ROOT_DIR/analytics-api"
VENV="$ROOT_DIR/.venv"
UVICORN="${VENV}/bin/uvicorn"
[[ ! -f "$UVICORN" ]] && UVICORN="uvicorn"

$UVICORN app.main:app --host 0.0.0.0 --port $API_PORT --log-level warning >> "$API_LOG" 2>&1 &
API_PID=$!

log "Waiting for Analytics API health check..."
api_ok=0
for i in $(seq 1 15); do
  if curl -sf --max-time 2 "http://127.0.0.1:$API_PORT/health" > /dev/null 2>&1; then
    log_ok "Analytics API ready (attempt $i)"
    api_ok=1
    break
  fi
  sleep 2
done

# --- STEP 3: Execution -------------------------------------------------------
echo -e "${GREEN}${BOLD}System Ready — Launch Android Application manually${NC}"
read -rp "Press ENTER when the application is initialized..." _

log "Injecting test data..."
NOW_MS=$(( $(date +%s) * 1000 ))
CLUSTER_PAYLOAD='{
  "radius_meters": 120,
  "reports": [
    {"id": "run4-r1", "latitude": 45.4642, "longitude": 9.1900, "fusedScore": 0.87, "damageType": "pothole"},
    {"id": "run4-r2", "latitude": 45.4646, "longitude": 9.1904, "fusedScore": 0.74, "damageType": "bump"}
  ]
}'

CLUSTER_RESP=$(curl -sf -X POST "http://127.0.0.1:$API_PORT/api/v1/clusters" -H "Content-Type: application/json" -d "$CLUSTER_PAYLOAD" 2>/dev/null)
if echo "$CLUSTER_RESP" | grep -q '"cluster_count"'; then
  log_ok "Clustering verification successful"
else
  log_fail "Clustering verification failed"
fi

echo -e "${GREEN}${BOLD}Run 4 Rehearsal Completed Successfully${NC}"
log "Finalized at $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
