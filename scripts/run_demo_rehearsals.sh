#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/output/statistics/demo_rehearsals"
REPORT_FILE="$ROOT_DIR/docs/LIVE_DEMO_REHEARSAL_LOG.md"
USE_DOCKER_TOOLING="${USE_DOCKER_TOOLING:-0}"

mkdir -p "$LOG_DIR"

cat > "$REPORT_FILE" <<'EOF'
# Live Demo Rehearsal Log

Date: 2026-04-20
Protocol source: docs/LIVE_DEMO_RUNBOOK.md

Pre-rehearsal fix applied:
- Allineata allowlist operator in firestore.rules per consentire update reali su campi `operatorId` e `notes` (oltre a `status`, `resolvedAt`, `resolvedBy`).

## Runs

| Run | Start (UTC) | End (UTC) | Android | Web | API | RBAC | Overall | Failed Step | Root Cause | Mitigation |
|---|---|---|---|---|---|---|---|---|---|---|
EOF

run_web_checks() {
  if [[ "$USE_DOCKER_TOOLING" == "1" ]]; then
    (cd "$ROOT_DIR" && docker compose --profile tooling run --rm tooling web-check)
  else
    (cd "$ROOT_DIR/web-portal" && npm run lint && npm run build)
  fi
}

run_rbac_checks() {
  if [[ "$USE_DOCKER_TOOLING" == "1" ]]; then
    (cd "$ROOT_DIR" && docker compose --profile tooling run --rm tooling rbac-check)
  else
    (cd "$ROOT_DIR" && npx -y firebase-tools emulators:exec --project roadguard-demo --only auth,firestore "node web-portal/scripts/auth-emulator-login-check.mjs && node web-portal/scripts/rbac-rules-check.mjs")
  fi
}

run_android_checks() {
  local log_file="$1"
  local gradle_cmd=(
    ./gradlew
    --no-daemon
    --console=plain
    :app:assembleDebug
    :app:testDebugUnitTest
    --tests
    "com.example.roadguard.sensor.NativeKalmanFilterTest"
    --tests
    "com.example.roadguard.integration.EndToEndIntegrationTest"
  )

  if (cd "$ROOT_DIR" && "${gradle_cmd[@]}") >"$log_file" 2>&1; then
    return 0
  fi

  echo "[rehearsal] Android checks failed on first attempt. Retrying once..." >>"$log_file"

  if (cd "$ROOT_DIR" && "${gradle_cmd[@]}") >>"$log_file" 2>&1; then
    return 0
  fi

  return 1
}

for run in 1 2 3; do
  start_ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  failed_step="-"
  root_cause="-"
  mitigation="-"
  overall="PASS"

  android_log="$LOG_DIR/run${run}-android.log"
  web_log="$LOG_DIR/run${run}-web.log"
  api_log="$LOG_DIR/run${run}-api.log"
  rbac_log="$LOG_DIR/run${run}-rbac.log"

  android_status="PASS"
  if ! run_android_checks "$android_log"; then
    android_status="FAIL"
    overall="FAIL"
    failed_step="Android"
    root_cause="Gradle build or test failed"
    mitigation="Inspect $android_log and fix test/build regression"
  fi

  web_status="PASS"
  if ! run_web_checks >"$web_log" 2>&1; then
    web_status="FAIL"
    overall="FAIL"
    if [[ "$failed_step" == "-" ]]; then
      failed_step="Web"
      root_cause="ESLint or web build failed"
      mitigation="Inspect $web_log and fix lint/build issue"
    fi
  fi

  api_status="PASS"
  if [[ "$overall" == "PASS" ]]; then
    now_ms=$(($(date +%s) * 1000))
    m1=$((now_ms - 30*24*3600*1000))
    m2=$((now_ms - 60*24*3600*1000))
    m3=$((now_ms - 90*24*3600*1000))

    {
      echo "--- HEALTH ---"
      curl -sS http://127.0.0.1:8001/health
      echo
      echo "--- CLUSTERS ---"
      curl -sS -X POST http://127.0.0.1:8001/api/v1/clusters \
        -H 'Content-Type: application/json' \
        -d '{"radius_meters":120,"reports":[{"id":"r1","latitude":45.4642,"longitude":9.1900,"fusedScore":0.84,"damageType":"pothole"},{"id":"r2","latitude":45.4646,"longitude":9.1904,"fusedScore":0.73,"damageType":"pothole"},{"id":"r3","latitude":45.4650,"longitude":9.1908,"fusedScore":0.79,"damageType":"bump"},{"id":"r4","latitude":41.9028,"longitude":12.4964,"fusedScore":0.52,"damageType":"roughness"}]}'
      echo
      echo "--- FORECAST ---"
      curl -sS -X POST http://127.0.0.1:8001/api/v1/forecast \
        -H 'Content-Type: application/json' \
        -d "{\"trend_months\":6,\"reports\":[{\"id\":\"f1\",\"fusedScore\":0.61,\"timestampMs\":$m3},{\"id\":\"f2\",\"fusedScore\":0.68,\"timestampMs\":$m2},{\"id\":\"f3\",\"fusedScore\":0.72,\"timestampMs\":$m1},{\"id\":\"f4\",\"fusedScore\":0.75,\"timestampMs\":$now_ms}]}"
      echo
    } >"$api_log" 2>&1

    if ! grep -q '"status":"ok"' "$api_log" || ! grep -q '"cluster_count"' "$api_log" || ! grep -q '"trend"' "$api_log"; then
      api_status="FAIL"
      overall="FAIL"
      if [[ "$failed_step" == "-" ]]; then
        failed_step="API"
        root_cause="Analytics API endpoint smoke test failed"
        mitigation="Inspect $api_log, restart API server, and re-run"
      fi
    fi
  else
    api_status="SKIP"
  fi

  rbac_status="PASS"
  if [[ "$overall" == "PASS" ]]; then
    if ! run_rbac_checks >"$rbac_log" 2>&1; then
      rbac_status="FAIL"
      overall="FAIL"
      if [[ "$failed_step" == "-" ]]; then
        failed_step="RBAC"
        root_cause="Firestore rules checks failed"
        mitigation="Inspect $rbac_log and align policy/client fields"
      fi
    elif ! grep -q 'Summary: 3/3 checks passed' "$rbac_log" || ! grep -q 'Summary: 6/6 checks passed' "$rbac_log"; then
      rbac_status="FAIL"
      overall="FAIL"
      if [[ "$failed_step" == "-" ]]; then
        failed_step="RBAC"
        root_cause="Auth/RBAC summary mismatch"
        mitigation="Review auth login and RBAC failures in $rbac_log"
      fi
    fi
  else
    rbac_status="SKIP"
  fi

  end_ts="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

  echo "| ${run} | ${start_ts} | ${end_ts} | ${android_status} | ${web_status} | ${api_status} | ${rbac_status} | ${overall} | ${failed_step} | ${root_cause} | ${mitigation} |" >> "$REPORT_FILE"
done

echo >> "$REPORT_FILE"
echo "Artifacts:" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run1-android.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run1-web.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run1-api.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run1-rbac.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run2-android.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run2-web.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run2-api.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run2-rbac.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run3-android.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run3-web.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run3-api.log" >> "$REPORT_FILE"
echo "- output/statistics/demo_rehearsals/run3-rbac.log" >> "$REPORT_FILE"

echo "Rehearsal report generated at $REPORT_FILE"
