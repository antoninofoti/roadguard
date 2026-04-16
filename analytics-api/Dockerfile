# ═══════════════════════════════════════════════════════════════════
# RoadGuard Analytics API — Multi-stage Dockerfile
# 
# Stage 1 (deps): install Python dependencies into a virtual env
# Stage 2 (runtime): copy venv into a minimal slim image
#
# Result: ~150MB image (vs ~900MB with full Python base)
# ═══════════════════════════════════════════════════════════════════

# ── Stage 1: dependency builder ─────────────────────────────────────
FROM python:3.12-slim AS builder

WORKDIR /app

# Install build tools (needed for some C-extension packages)
RUN apt-get update && apt-get install -y --no-install-recommends gcc && \
    rm -rf /var/lib/apt/lists/*

# Create virtualenv inside /app/venv
RUN python -m venv /app/venv
ENV PATH="/app/venv/bin:$PATH"

# Copy requirements and install (layer-cached unless requirements.txt changes)
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt


# ── Stage 2: runtime image ──────────────────────────────────────────
FROM python:3.12-slim AS runtime

LABEL org.opencontainers.image.title="RoadGuard Analytics API"
LABEL org.opencontainers.image.description="FastAPI microservice for spatial clustering and trend forecasting"
LABEL org.opencontainers.image.version="1.0.0"

WORKDIR /app

# Create non-root user for security (Principle of Least Privilege)
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy virtualenv from builder
COPY --from=builder /app/venv /app/venv
ENV PATH="/app/venv/bin:$PATH"

# Copy application source
COPY app/ ./app/

# Switch to non-root user
USER appuser

EXPOSE 8000

# Health check for Docker runtime (Kubernetes uses its own probe)
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
