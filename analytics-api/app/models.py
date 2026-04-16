"""
Pydantic schemas for the Analytics API.

Mirrors the Kotlin data models:
  - Report      → com.example.roadguard.model.Report
  - DamageCluster → com.example.roadguard.analytics.DamageCluster
  - DegradationForecast → com.example.roadguard.analytics.DegradationForecast
"""

from __future__ import annotations
from pydantic import BaseModel, Field
from typing import Literal


# ── Input Models ─────────────────────────────────────────────────────────────

class GeoPoint(BaseModel):
    latitude: float = Field(..., ge=-90.0, le=90.0)
    longitude: float = Field(..., ge=-180.0, le=180.0)


class ReportIn(BaseModel):
    """
    A road damage report as sent by the web-portal.
    Mirrors com.example.roadguard.model.Report exactly.
    """
    id: str = ""
    latitude: float | None = None
    longitude: float | None = None
    severity: float = Field(0.0, ge=0.0, le=1.0)
    fusedScore: float = Field(0.0, ge=0.0, le=1.0)
    cvConfidence: float = Field(0.0, ge=0.0, le=1.0)
    sensorConfidence: float = Field(0.0, ge=0.0, le=1.0)
    damageType: str = ""
    status: Literal["PENDING", "CONFIRMED", "REJECTED", "RESOLVED"] = "PENDING"
    detectionSource: Literal["CV_ONLY", "SENSOR_ONLY", "DUAL_CONFIRMED", "MANUAL"] = "MANUAL"
    timestampMs: int | None = None   # Unix millis from Firestore Timestamp.toMillis()


class ClusterRequest(BaseModel):
    reports: list[ReportIn]
    radius_meters: float = Field(100.0, gt=0, description="Cluster radius in metres")


class ForecastRequest(BaseModel):
    reports: list[ReportIn]
    trend_months: int = Field(6, ge=2, le=24, description="Months of history to analyse")


# ── Output Models ─────────────────────────────────────────────────────────────

class DamageCluster(BaseModel):
    """Mirrors com.example.roadguard.analytics.DamageCluster."""
    id: int
    center: GeoPoint
    report_ids: list[str]
    radius_meters: float
    avg_fused_score: float
    dominant_type: str
    report_count: int


class ClusterResponse(BaseModel):
    cluster_count: int
    clusters: list[DamageCluster]
    unclustered_count: int


class TrendPoint(BaseModel):
    """Mirrors com.example.roadguard.analytics.TrendPoint."""
    month_offset: int
    report_count: int
    avg_severity: float


class ForecastResponse(BaseModel):
    """Mirrors com.example.roadguard.analytics.DegradationForecast."""
    slope: float
    predicted_next_month: float
    trend: Literal["IMPROVING", "STABLE", "DEGRADING"]
    monthly_data: list[TrendPoint]
    r_squared: float = Field(..., description="Coefficient of determination for regression quality")
