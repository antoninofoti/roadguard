FROM cgr.dev/chainguard/wolfi-base:latest

# Firebase Firestore emulator requires Java runtime.
RUN apk add --no-cache \
    bash \
    ca-certificates \
    curl \
    nodejs-24 \
    npm \
    openjdk-21-jre

WORKDIR /workspace

# Default command is overridden by docker compose run command args.
CMD ["bash", "-lc", "node --version && npm --version && java -version"]
