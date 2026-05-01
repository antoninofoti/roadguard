#!/bin/bash
# ============================================================================
# Firebase Environment Validation Script
# ============================================================================
# Validates that all required VITE_FIREBASE_* environment variables are set
# before Vite build. Runs during CI/CD and pre-build checks.
#
# Usage: ./validate-firebase-env.sh [env-file]
# Default env-file: .env.production
# ============================================================================

set -e

ENV_FILE="${1:-.env.production}"

# CRITICAL Firebase variables that must not be blank
REQUIRED_VARS=(
    "VITE_FIREBASE_API_KEY"
    "VITE_FIREBASE_AUTH_DOMAIN"
    "VITE_FIREBASE_PROJECT_ID"
    "VITE_FIREBASE_STORAGE_BUCKET"
    "VITE_FIREBASE_MESSAGING_SENDER_ID"
    "VITE_FIREBASE_APP_ID"
    "VITE_AUTH_MODE"
)

echo "Validating Firebase environment configuration..."
echo "   Config file: $ENV_FILE"
echo ""

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $ENV_FILE not found"
    echo "   Create from .env.example: cp .env.example $ENV_FILE"
    exit 1
fi

ERRORS=0
WARNINGS=0

AUTH_MODE=$(grep "^VITE_AUTH_MODE=" "$ENV_FILE" | cut -d'=' -f2- | xargs 2>/dev/null || echo "")

for var in "${REQUIRED_VARS[@]}"; do
    # Extract value from env file (handles = without spaces)
    VALUE=$(grep "^${var}=" "$ENV_FILE" | cut -d'=' -f2- | xargs 2>/dev/null || echo "")
    
    if [ -z "$VALUE" ]; then
        echo "CRITICAL: $var is not set or is empty"
        ERRORS=$((ERRORS + 1))
    elif [ "$VALUE" = "demo-api-key" ] || [ "$VALUE" = "000000000000" ]; then
        if [ "$AUTH_MODE" = "emulator" ]; then
            echo "$var configured (demo value accepted in emulator mode)"
        else
            echo "WARNING: $var is using demo/placeholder value: $VALUE"
            WARNINGS=$((WARNINGS + 1))
        fi
    else
        echo "$var configured"
    fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $ERRORS -gt 0 ]; then
    echo "VALIDATION FAILED: $ERRORS critical errors, $WARNINGS warnings"
    echo ""
    echo "   Fix: Populate Firebase variables in $ENV_FILE"
    echo "   Source: firebase.json or Firebase Console"
    exit 1
elif [ $WARNINGS -gt 0 ]; then
    echo "VALIDATION WARNING: $WARNINGS warnings (demo values detected)"
    echo "   For production deployment, use actual Firebase credentials"
    echo "   Proceeding with build (demo mode)..."
else
    echo "VALIDATION PASSED: All Firebase variables configured"
fi

exit 0
