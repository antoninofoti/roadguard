"""
RoadGuard Analytics API — FastAPI microservice (Phase G.3).

Provides server-side analytics over road damage report data:
  - POST /api/v1/clusters  — DBSCAN-inspired spatial clustering
  - POST /api/v1/forecast  — Linear regression trend forecast
  - GET  /health           — Liveness probe for Kubernetes

Data arrives as JSON from the Operator Web Portal (which already
holds live Firestore reports in the browser). No Firebase Admin SDK
needed — the browser pushes the data to this service via POST.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import clusters, forecast

app = FastAPI(
    title="RoadGuard Analytics API",
    description=(
        "Polyglot microservice providing spatial clustering and degradation "
        "trend forecasting for road damage reports collected by the "
        "RoadGuard Android edge application."
    ),
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# ── CORS ────────────────────────────────────────────────────────────────────
# In production, restrict to the web-portal origin.
# Configurable via ALLOWED_ORIGINS env var (comma-separated).
import os

_origins_raw = os.getenv("ALLOWED_ORIGINS", "http://localhost:3000,http://localhost:5173")
origins = [o.strip() for o in _origins_raw.split(",")]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# ── Routers ──────────────────────────────────────────────────────────────────
app.include_router(clusters.router, prefix="/api/v1", tags=["Spatial Clustering"])
app.include_router(forecast.router, prefix="/api/v1", tags=["Trend Forecast"])


# ── Health / Liveness probe ──────────────────────────────────────────────────
@app.get("/health", tags=["Observability"])
def health_check():
    """Kubernetes liveness and readiness probe."""
    return {"status": "ok", "service": "roadguard-analytics-api", "version": "1.0.0"}
