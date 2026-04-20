FROM node:24-bookworm-slim

# Firebase Firestore emulator requires Java runtime.
RUN apt-get update \
    && apt-get dist-upgrade -y \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless ca-certificates bash curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Default command is overridden by docker compose run command args.
CMD ["bash", "-lc", "node --version && npm --version && java -version"]
