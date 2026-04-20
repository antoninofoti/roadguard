#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/workspace"
WEB_DIR="$ROOT_DIR/web-portal"

usage() {
  cat <<'USAGE'
Usage: scripts/docker_tooling.sh <command>

Commands:
  web-check      Run npm ci + lint + build for web-portal
  rbac-check     Run Auth Emulator login checks + Firestore RBAC checks
  closing-checks Run web-check and rbac-check sequentially
USAGE
}

require_web_dir() {
  if [[ ! -f "$WEB_DIR/package.json" ]]; then
    echo "[docker-tooling] Missing $WEB_DIR/package.json"
    exit 1
  fi
}

npm_ci_with_retry() {
  local attempt=1
  local max_attempts=2

  while [[ "$attempt" -le "$max_attempts" ]]; do
    if npm ci; then
      return 0
    fi

    if [[ "$attempt" -lt "$max_attempts" ]]; then
      echo "[docker-tooling] npm ci failed (attempt $attempt/$max_attempts). Cleaning node_modules and retrying..."
      rm -rf "$WEB_DIR/node_modules"
    fi

    attempt=$((attempt + 1))
  done

  echo "[docker-tooling] npm ci failed after $max_attempts attempts"
  return 1
}

run_web_check() {
  require_web_dir
  echo "[docker-tooling] Running web lint/build in container"
  cd "$WEB_DIR"
  npm_ci_with_retry
  npm run lint
  npm run build
}

run_rbac_check() {
  require_web_dir
  if [[ ! -f "$ROOT_DIR/firebase.json" ]]; then
    echo "[docker-tooling] Missing $ROOT_DIR/firebase.json"
    exit 1
  fi

  echo "[docker-tooling] Running Auth Emulator login checks + Firestore RBAC checks in container"
  cd "$WEB_DIR"
  npm_ci_with_retry
  cd "$ROOT_DIR"

  npx -y firebase-tools emulators:exec \
    --project roadguard-demo \
    --only auth,firestore \
    "node web-portal/scripts/auth-emulator-login-check.mjs && node web-portal/scripts/rbac-rules-check.mjs"
}

case "${1:-}" in
  web-check)
    run_web_check
    ;;
  rbac-check)
    run_rbac_check
    ;;
  closing-checks)
    run_web_check
    run_rbac_check
    ;;
  *)
    usage
    exit 1
    ;;
esac
