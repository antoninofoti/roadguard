#!/usr/bin/env bash
# scripts/fix_gradle.sh
# Ensures Gradle has a writable environment to avoid lock file errors.

set -e

# Set a local Gradle user home inside the workspace
export GRADLE_USER_HOME="$(pwd)/.gradle-local"

echo "Setting GRADLE_USER_HOME to $GRADLE_USER_HOME"

# Create the directory if it doesn't exist
mkdir -p "$GRADLE_USER_HOME"

# Ensure correct permissions
chmod -R 755 "$GRADLE_USER_HOME"

echo "Gradle environment configured successfully."
