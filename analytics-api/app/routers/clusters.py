"""
Spatial clustering router.

POST /api/v1/clusters

Implements a greedy distance-based clustering algorithm that mirrors
the Kotlin PredictiveAnalytics.identifyDamageClusters() method.
The algorithm is intentionally kept simple and deterministic
(no sklearn dependency) to keep the Docker image minimal.

For production use with large datasets, replace with proper DBSCAN
from scikit-learn — the interface is identical.
"""

from fastapi import APIRouter
from math import radians, sin, cos, sqrt, asin
from app.models import ClusterRequest, ClusterResponse, DamageCluster, GeoPoint, ReportIn

router = APIRouter()

EARTH_RADIUS_M = 6_371_000.0


def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Haversine distance in metres — mirrors PredictiveAnalytics.haversineDistance()."""
    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)
    a = sin(dlat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_M * asin(sqrt(a))


def centroid(reports: list[ReportIn]) -> GeoPoint:
    lats = [r.latitude for r in reports if r.latitude is not None]
    lons = [r.longitude for r in reports if r.longitude is not None]
    if not lats:
        return GeoPoint(latitude=0.0, longitude=0.0)
    return GeoPoint(latitude=sum(lats) / len(lats), longitude=sum(lons) / len(lons))


@router.post("/clusters", response_model=ClusterResponse, summary="Spatial damage clustering")
def compute_clusters(body: ClusterRequest) -> ClusterResponse:
    """
    Groups geo-tagged damage reports into spatial clusters using a greedy
    nearest-neighbour approach identical to the on-device Kotlin algorithm.

    Only reports with valid GPS coordinates are clustered.
    Reports within `radius_meters` of a cluster seed are merged into that cluster.
    Singletons (clusters of 1) are returned as unclustered.
    """
    geo_reports = [r for r in body.reports if r.latitude is not None and r.longitude is not None]
    unassigned = list(geo_reports)
    clusters: list[DamageCluster] = []
    unclustered_count = 0
    cluster_id = 0

    while unassigned:
        seed = unassigned.pop(0)
        members = [seed]

        remaining = []
        for candidate in unassigned:
            dist = haversine(seed.latitude, seed.longitude, candidate.latitude, candidate.longitude)
            if dist <= body.radius_meters:
                members.append(candidate)
            else:
                remaining.append(candidate)
        unassigned = remaining

        if len(members) < 2:
            unclustered_count += 1
            continue

        center = centroid(members)
        avg_score = sum(r.fusedScore for r in members) / len(members)

        # Dominant damage type — mirrors Kotlin .groupingBy { it }.eachCount()
        type_counts: dict[str, int] = {}
        for r in members:
            t = r.damageType or "unknown"
            type_counts[t] = type_counts.get(t, 0) + 1
        dominant = max(type_counts, key=type_counts.get)

        # Effective radius = max distance from centroid
        effective_radius = max(
            haversine(center.latitude, center.longitude, r.latitude, r.longitude)
            for r in members
        )

        clusters.append(DamageCluster(
            id=cluster_id,
            center=center,
            report_ids=[r.id for r in members],
            radius_meters=effective_radius,
            avg_fused_score=round(avg_score, 4),
            dominant_type=dominant,
            report_count=len(members),
        ))
        cluster_id += 1

    clusters.sort(key=lambda c: c.report_count, reverse=True)

    return ClusterResponse(
        cluster_count=len(clusters),
        clusters=clusters,
        unclustered_count=unclustered_count,
    )
