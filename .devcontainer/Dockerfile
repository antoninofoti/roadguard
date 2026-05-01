# ═══════════════════════════════════════════════════════════════════════════════
# RoadGuard — Dev Container
#
# Provides a self-contained development environment with:
#   • Java 17         — Android Gradle builds
#   • Android SDK 34  — platform & build-tools
#   • NDK 26.3        — C/JNI compilation (kalman_filter + jni_bridge)
#   • CMake 3.22.1    — matches version pinned in CMakeLists.txt
#   • Node 22 LTS     — web-portal (React/Vite/TypeScript)
#   • Python 3.12     — analysis/ and analytics-api/ scripts
#
# NOTE: This image is for DEVELOPMENT ONLY.
#       Production images live in analytics-api/Dockerfile and web-portal/Dockerfile.
# ═══════════════════════════════════════════════════════════════════════════════

FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

ARG DEBIAN_FRONTEND=noninteractive

# ── System packages ────────────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Android/Java build tools
    openjdk-17-jdk \
    # Fetch & unpack utilities
    wget \
    unzip \
    curl \
    # C/C++ toolchain (for NDK cross-compile validation)
    cmake \
    ninja-build \
    # Python runtime
    python3.12 \
    python3.12-venv \
    python3-pip \
    # ADB (USB debugging inside container with --privileged)
    adb \
    # Misc
    git \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ── Node.js 22 LTS ────────────────────────────────────────────────────────────
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# ── Android SDK ────────────────────────────────────────────────────────────────
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_NDK_VERSION=26.3.11579264
ENV ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/${ANDROID_NDK_VERSION}

# Add SDK tools to PATH so sdkmanager/adb are available system-wide
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Download Android command-line tools (version 11076708 = stable 2024)
ARG CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && wget -q "${CMDLINE_TOOLS_URL}" -O /tmp/cmdtools.zip \
    && unzip -q /tmp/cmdtools.zip -d /tmp/cmdtools_unzip \
    && mv /tmp/cmdtools_unzip/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm -rf /tmp/cmdtools_unzip /tmp/cmdtools.zip

# Accept all SDK licenses, then install required packages.
# cmake;3.22.1 matches the version pinned in app/src/main/cpp/CMakeLists.txt.
RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "ndk;${ANDROID_NDK_VERSION}" \
        "cmake;3.22.1"

# ── Python virtualenvs ─────────────────────────────────────────────────────────
# Two isolated venvs: one per Python sub-project so dependency sets never clash.
#   /opt/venvs/analysis      ← analysis/ (numpy, pandas, matplotlib, …)
#   /opt/venvs/analytics-api ← analytics-api/ (fastapi, uvicorn, pydantic, …)
RUN mkdir -p /opt/venvs \
    && python3.12 -m venv /opt/venvs/analysis \
    && python3.12 -m venv /opt/venvs/analytics-api \
    && chown -R vscode:vscode /opt/venvs

# ── JAVA_HOME ─────────────────────────────────────────────────────────────────
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# ── Gradle cache preservation ─────────────────────────────────────────────────
# Mounting gradle cache here avoids re-downloading on every container rebuild.
VOLUME /root/.gradle

# Switch to the non-root devcontainer user (created by the base image)
USER vscode
