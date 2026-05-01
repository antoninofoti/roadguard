#!/usr/bin/env bash
# scripts/setup_env.sh
# Interactive setup for Web Portal environment variables

set -e

cd "$(dirname "$0")/.."

ENV_FILE=".env.docker"
EXAMPLE_FILE=".env.docker.example"

if [ ! -f "$EXAMPLE_FILE" ]; then
    echo "Error: $EXAMPLE_FILE not found!"
    exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
    echo "Creating $ENV_FILE from example..."
    cp "$EXAMPLE_FILE" "$ENV_FILE"
fi

echo "--- RoadGuard Environment Setup ---"
echo "Please enter the following Firebase credentials (press enter to keep existing/default values):"

vars=(
    "VITE_FIREBASE_API_KEY"
    "VITE_FIREBASE_AUTH_DOMAIN"
    "VITE_FIREBASE_PROJECT_ID"
    "VITE_FIREBASE_STORAGE_BUCKET"
    "VITE_FIREBASE_MESSAGING_SENDER_ID"
    "VITE_FIREBASE_APP_ID"
)

for var in "${vars[@]}"; do
    current_val=$(grep "^${var}=" "$ENV_FILE" | cut -d'=' -f2-)
    read -p "$var [$current_val]: " input_val
    if [ -n "$input_val" ]; then
        # Update the value
        sed -i "s|^${var}=.*|${var}=${input_val}|" "$ENV_FILE"
    fi
done

echo ""
echo "Done! Environment configured in $ENV_FILE"
