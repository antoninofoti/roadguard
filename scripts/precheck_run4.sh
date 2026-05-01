#!/usr/bin/env bash
# =============================================================================
# scripts/precheck_run4.sh
# RoadGuard — Pre-Run4 Automated Checklist
# Verifies all system prerequisites before the final demo rehearsal.
# =============================================================================
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PASS=0
FAIL=0
WARNINGS=()
FAILURES=()

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

banner() {
  echo -e "${CYAN}${BOLD}"
  echo "----------------------------------------------------------------"
  echo "         RoadGuard — Pre-Run4 Automated Checklist               "
  echo "----------------------------------------------------------------"
  echo -e "${NC}"
}

check_pass() {
  echo -e "  [PASS]  $1"
  ((PASS++))
}

check_fail() {
  echo -e "  [FAIL]  $1"
  FAILURES+=("$1")
  ((FAIL++))
}

check_warn() {
  echo -e "  [WARN]  $1"
  WARNINGS+=("$1")
}

banner

# --- CHECK 1: fix_gradle.sh existence and execution --------------------------
echo -e "${BOLD}[1/5] Verifying fix_gradle.sh${NC}"
GRADLE_SCRIPT="$ROOT_DIR/scripts/fix_gradle.sh"
GRADLE_LOCAL="$ROOT_DIR/.gradle-local"

if [[ -f "$GRADLE_SCRIPT" ]]; then
  check_pass "scripts/fix_gradle.sh exists"
  if [[ -d "$GRADLE_LOCAL" ]]; then
    check_pass ".gradle-local/ exists (script already executed)"
  else
    check_warn ".gradle-local/ not found — run: bash scripts/fix_gradle.sh"
  fi
else
  check_fail "scripts/fix_gradle.sh NOT FOUND"
fi
echo

# --- CHECK 2: .env.docker with VITE_FIREBASE_* variables ---------------------
echo -e "${BOLD}[2/5] Verifying .env.docker${NC}"
ENV_FILE="$ROOT_DIR/.env.docker"
REQUIRED_VARS=(
  VITE_FIREBASE_API_KEY
  VITE_FIREBASE_AUTH_DOMAIN
  VITE_FIREBASE_PROJECT_ID
  VITE_FIREBASE_STORAGE_BUCKET
  VITE_FIREBASE_MESSAGING_SENDER_ID
  VITE_FIREBASE_APP_ID
)

if [[ ! -f "$ENV_FILE" ]]; then
  check_fail ".env.docker not found — copy from .env.docker.example"
else
  check_pass ".env.docker exists"
  env_fail=0
  for var in "${REQUIRED_VARS[@]}"; do
    line=$(grep "^${var}=" "$ENV_FILE" || true)
    if [[ -z "$line" ]]; then
      check_fail "  Missing variable: $var"
      env_fail=1
    else
      value="${line#*=}"
      if [[ "$value" == *"your_"* ]] || [[ -z "$value" ]]; then
        check_warn "  $var contains placeholder values (acceptable for emulator mode)"
      else
        check_pass "  $var is configured"
      fi
    fi
  done
  if [[ $env_fail -eq 0 ]]; then
    check_pass "All VITE_FIREBASE_* variables are present in .env.docker"
  fi
fi
echo

# --- CHECK 3: TFLite model size ----------------------------------------------
echo -e "${BOLD}[3/5] Verifying TFLite model files${NC}"
ASSETS_DIR="$ROOT_DIR/app/src/main/assets"
TFLITE_FILES=(
  "$ASSETS_DIR/pothole-y8objdect_float16.tflite"
  "$ASSETS_DIR/pothole-segm_float32.tflite"
)

tflite_found=0
for f in "${TFLITE_FILES[@]}"; do
  if [[ -f "$f" ]]; then
    size=$(stat -c%s "$f")
    if [[ $size -gt 1048576 ]]; then
      check_pass "$(basename "$f") — $(numfmt --to=iec-i --suffix=B $size)"
      tflite_found=1
    else
      check_fail "$(basename "$f") size is too small: ${size} bytes (required > 1 MB)"
    fi
  fi
done

if [[ $tflite_found -eq 0 ]]; then
  check_fail "No valid TFLite models found in app/src/main/assets/"
fi
echo

# --- CHECK 4: evaluation results ---------------------------------------------
echo -e "${BOLD}[4/5] Verifying evaluation results${NC}"
CSV_FILE="$ROOT_DIR/evaluation/results/comparison_table.csv"
if [[ -f "$CSV_FILE" ]]; then
  rows=$(wc -l < "$CSV_FILE")
  check_pass "comparison_table.csv found ($rows lines)"
  if grep -qi "late.*fusion\|fusion" "$CSV_FILE"; then
    check_pass "Late Fusion entry present in metrics table"
  else
    check_warn "Late Fusion entry not found in CSV"
  fi
else
  check_fail "comparison_table.csv NOT FOUND — execute: python evaluation/run_all.py"
fi
echo

# --- CHECK 5: Analytics API status -------------------------------------------
echo -e "${BOLD}[5/5] Verifying Analytics API status${NC}"
if curl -sf --max-time 2 http://127.0.0.1:8000/health > /dev/null 2>&1; then
  health=$(curl -sf --max-time 2 http://127.0.0.1:8000/health)
  check_pass "Analytics API is active: $health"
else
  check_warn "Analytics API not active on port 8000 (standard if not yet started)"
fi
echo

# --- SUMMARY -----------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------${NC}"
echo -e "${BOLD}  PRE-CHECKLIST RUN4 SUMMARY${NC}"
echo -e "${BOLD}----------------------------------------------------------------${NC}"
echo -e "  PASS: $PASS  |  FAIL: $FAIL  |  WARN: ${#WARNINGS[@]}"
echo

if [[ $FAIL -gt 0 ]]; then
  echo -e "${RED}${BOLD}CRITICAL ISSUES DETECTED:${NC}"
  for f in "${FAILURES[@]}"; do
    echo -e "  - $f"
  done
  echo
  exit 1
else
  if [[ ${#WARNINGS[@]} -gt 0 ]]; then
    echo -e "${YELLOW}Warnings (non-blocking):${NC}"
    for w in "${WARNINGS[@]}"; do
      echo -e "  - $w"
    done
    echo
  fi
  echo -e "${GREEN}${BOLD}System status: OK — Proceed with: bash scripts/run4_demo.sh${NC}"
  exit 0
fi
