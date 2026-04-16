"""
Degradation trend forecast router.

POST /api/v1/forecast

Implements linear regression over monthly report counts, mirroring
PredictiveAnalytics.predictDegradationTrend() in Kotlin.

Returns slope, prediction for next month, R², and raw monthly data.
"""

from fastapi import APIRouter
from datetime import datetime, timezone
from app.models import ForecastRequest, ForecastResponse, TrendPoint

router = APIRouter()


def linear_regression(x: list[float], y: list[float]) -> tuple[float, float, float]:
    """
    OLS linear regression: y = slope * x + intercept.
    Returns (slope, intercept, r_squared).
    Mirrors PredictiveAnalytics.linearRegression() in Kotlin.
    """
    n = len(x)
    if n < 2:
        return 0.0, (y[0] if y else 0.0), 0.0

    x_mean = sum(x) / n
    y_mean = sum(y) / n

    ss_xy = sum((x[i] - x_mean) * (y[i] - y_mean) for i in range(n))
    ss_xx = sum((x[i] - x_mean) ** 2 for i in range(n))
    ss_yy = sum((y[i] - y_mean) ** 2 for i in range(n))

    slope = ss_xy / ss_xx if ss_xx != 0 else 0.0
    intercept = y_mean - slope * x_mean

    # R² — coefficient of determination
    r_squared = (ss_xy ** 2 / (ss_xx * ss_yy)) if (ss_xx * ss_yy) > 0 else 0.0

    return slope, intercept, round(min(r_squared, 1.0), 4)


@router.post("/forecast", response_model=ForecastResponse, summary="Road degradation trend forecast")
def compute_forecast(body: ForecastRequest) -> ForecastResponse:
    """
    Analyses the temporal distribution of road damage reports and fits a
    linear regression to detect improving, stable, or degrading trends.

    Reports are bucketed by calendar month. Slope > 0.5 → DEGRADING,
    slope < -0.5 → IMPROVING, otherwise STABLE.

    Mirrors PredictiveAnalytics.predictDegradationTrend() in Kotlin,
    extended with R² for statistical validity reporting in the thesis.
    """
    now = datetime.now(tz=timezone.utc)
    # Month index: year * 12 + month (0-indexed)
    current_month_idx = now.year * 12 + (now.month - 1)

    monthly: dict[int, list[float]] = {}
    for offset in range(-body.trend_months + 1, 1):
        monthly[current_month_idx + offset] = []

    for report in body.reports:
        if report.timestampMs is None:
            continue
        dt = datetime.fromtimestamp(report.timestampMs / 1000.0, tz=timezone.utc)
        month_idx = dt.year * 12 + (dt.month - 1)
        if month_idx in monthly:
            monthly[month_idx].append(report.fusedScore)

    trend_points: list[TrendPoint] = []
    for offset in range(-body.trend_months + 1, 1):
        idx = current_month_idx + offset
        bucket = monthly.get(idx, [])
        trend_points.append(TrendPoint(
            month_offset=offset,
            report_count=len(bucket),
            avg_severity=round(sum(bucket) / len(bucket), 4) if bucket else 0.0,
        ))

    x = [float(p.month_offset) for p in trend_points]
    y = [float(p.report_count) for p in trend_points]
    slope, intercept, r_squared = linear_regression(x, y)

    predicted = max(0.0, slope * 1.0 + intercept)

    if slope > 0.5:
        trend = "DEGRADING"
    elif slope < -0.5:
        trend = "IMPROVING"
    else:
        trend = "STABLE"

    return ForecastResponse(
        slope=round(slope, 4),
        predicted_next_month=round(predicted, 2),
        trend=trend,
        monthly_data=trend_points,
        r_squared=r_squared,
    )
