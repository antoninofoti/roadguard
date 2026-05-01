#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ROOT_DIR}/.android-sdk"
GRADLE_HOME="${ROOT_DIR}/.gradle-local"
SDKMANAGER_BIN="${ANDROID_SDKMANAGER_BIN:-/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager}"

if [[ ! -x "${SDKMANAGER_BIN}" ]]; then
  echo "ERROR: sdkmanager not found at ${SDKMANAGER_BIN}" >&2
  echo "Set ANDROID_SDKMANAGER_BIN to a valid sdkmanager path and retry." >&2
  exit 1
fi

mkdir -p "${SDK_ROOT}" "${GRADLE_HOME}"

# Install exactly what AGP 9.1 + this project need in a writable SDK root.
yes | "${SDKMANAGER_BIN}" --sdk_root="${SDK_ROOT}" --licenses >/dev/null || true
"${SDKMANAGER_BIN}" --sdk_root="${SDK_ROOT}" \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;36.0.0" \
  "ndk;26.3.11579264" \
  "cmake;3.22.1"

maps_api_key_line="MAPS_API_KEY="
if [[ -f "${ROOT_DIR}/local.properties" ]]; then
  existing_key_line="$(grep '^MAPS_API_KEY=' "${ROOT_DIR}/local.properties" || true)"
  if [[ -n "${existing_key_line}" ]]; then
    maps_api_key_line="${existing_key_line}"
  fi
fi

cat > "${ROOT_DIR}/local.properties" <<EOF
sdk.dir=${SDK_ROOT}
${maps_api_key_line}
EOF

cd "${ROOT_DIR}"
GRADLE_USER_HOME="${GRADLE_HOME}" ./gradlew :app:assembleDebug --console=plain --no-daemon
