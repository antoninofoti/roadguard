#!/usr/bin/env bash
# .devcontainer/post-create.sh
# ─────────────────────────────────────────────────────────────────────────────
# Executed ONCE after the dev container is created (postCreateCommand).
# Installs all project dependencies into the pre-created Python venvs and
# bootstraps the web-portal Node modules.
#
# This is separate from the Dockerfile so that dependency updates (e.g. adding
# a new Python package to requirements.txt) only require re-running this script,
# not a full image rebuild.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

WORKSPACE="/workspaces/RoadGuard"
CYAN='\033[0;36m'
GREEN='\033[0;32m'
NC='\033[0m' # No Colour

log() { echo -e "${CYAN}[devcontainer]${NC} $*"; }
ok()  { echo -e "${GREEN}[devcontainer]${NC} ✅ $*"; }

# ── Python: analysis/ ─────────────────────────────────────────────────────────
log "Installing Python deps for analysis/ ..."
/opt/venvs/analysis/bin/pip install --upgrade pip --quiet
/opt/venvs/analysis/bin/pip install \
    --requirement "${WORKSPACE}/analysis/requirements.txt" \
    --quiet
ok "analysis/ venv ready  →  /opt/venvs/analysis"

# ── Python: analytics-api/ ────────────────────────────────────────────────────
log "Installing Python deps for analytics-api/ ..."
/opt/venvs/analytics-api/bin/pip install --upgrade pip --quiet
/opt/venvs/analytics-api/bin/pip install \
    --requirement "${WORKSPACE}/analytics-api/requirements.txt" \
    --quiet
ok "analytics-api/ venv ready  →  /opt/venvs/analytics-api"

# ── Node: web-portal/ ─────────────────────────────────────────────────────────
log "Running npm ci for web-portal/ ..."
cd "${WORKSPACE}/web-portal"
npm ci --prefer-offline --silent
cd "${WORKSPACE}"
ok "web-portal node_modules installed"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}════════════════════════════════════════════${NC}"
echo -e "${GREEN}  RoadGuard dev container ready! 🚀         ${NC}"
echo -e "${GREEN}════════════════════════════════════════════${NC}"
echo ""
echo "  Android SDK : ${ANDROID_HOME}"
echo "  NDK         : ${ANDROID_NDK_HOME}"
echo "  Java        : $(java -version 2>&1 | head -1)"
echo "  Node        : $(node --version)"
echo "  Python      : $(/opt/venvs/analysis/bin/python --version)"
echo ""
