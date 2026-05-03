/**
 * useAnalytics — Derives analytics data from Firestore reports.
 *
 * Mirrors the logic of PredictiveAnalytics.kt (Android) on the web side:
 *  - Summary statistics (totals, averages, % dual confirmed)
 *  - Top-5 priority zones (geohash-based, priority = avgSeverity × log2(n+1) × decay)
 *  - Top-5 damage clusters (greedy 100-m clustering with Haversine distance)
 *  - 6-month degradation trend (OLS slope → IMPROVING / STABLE / DEGRADING)
 *
 * All computations are client-side to avoid additional cloud infrastructure.
 */

import { useMemo } from 'react';
import type { Report } from '../types/report';

// ── Types ────────────────────────────────────────────────────────────────────

export interface AnalyticsSummary {
  total: number;
  pending: number;
  confirmed: number;
  rejected: number;
  resolved: number;
  dualConfirmedPct: number;   // 0–100
  avgFusedScore: number;       // 0–1
}

export interface PriorityZone {
  geohash: string;
  label: string;              // e.g. "Zone 48.3, 11.5"
  reportCount: number;
  confirmedCount: number;
  avgSeverity: number;
  daysSinceLatest: number;
  priorityScore: number;
}

export interface DamageCluster {
  centroidLat: number;
  centroidLng: number;
  count: number;
  dominantType: string;
  avgFusedScore: number;
  radiusM: number;
}

export type TrendClassification = 'IMPROVING' | 'STABLE' | 'DEGRADING';

export interface MonthlyPoint {
  label: string;   // e.g. "Nov 2025"
  count: number;
}

export interface DegradationForecast {
  monthly: MonthlyPoint[];
  slope: number;
  trend: TrendClassification;
  nextMonthPredicted: number;
}

export interface AnalyticsData {
  summary: AnalyticsSummary;
  priorityZones: PriorityZone[];
  clusters: DamageCluster[];
  forecast: DegradationForecast;
}

// ── Constants ─────────────────────────────────────────────────────────────────
const CLUSTER_RADIUS_M = 100;
const TREND_MONTHS = 6;
const GEOHASH_PRECISION = 2;   // degrees × 10 → ~10 km cells (rough grid)

// ── Haversine distance (metres) ───────────────────────────────────────────────
function haversine(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

// ── Simplified geohash (grid cell key) ────────────────────────────────────────
function gridKey(lat: number, lng: number): string {
  const latQ = Math.floor(lat * GEOHASH_PRECISION) / GEOHASH_PRECISION;
  const lngQ = Math.floor(lng * GEOHASH_PRECISION) / GEOHASH_PRECISION;
  return `${latQ.toFixed(1)},${lngQ.toFixed(1)}`;
}

// ── OLS linear regression ────────────────────────────────────────────────────
function olsSlope(xs: number[], ys: number[]): number {
  const n = xs.length;
  if (n < 2) return 0;
  const xMean = xs.reduce((a, b) => a + b, 0) / n;
  const yMean = ys.reduce((a, b) => a + b, 0) / n;
  const num = xs.reduce((s, x, i) => s + (x - xMean) * (ys[i] - yMean), 0);
  const den = xs.reduce((s, x) => s + (x - xMean) ** 2, 0);
  return den === 0 ? 0 : num / den;
}

// ── Priority decay function ───────────────────────────────────────────────────
function decayFactor(daysSince: number): number {
  return Math.pow(0.5, daysSince / 30);
}

// ── Main hook ────────────────────────────────────────────────────────────────
export function useAnalytics(reports: Report[]): AnalyticsData {
  return useMemo(() => {
    const now = Date.now();

    // ── Summary ──────────────────────────────────────────────────────────────
    const summary: AnalyticsSummary = {
      total: reports.length,
      pending:   reports.filter(r => r.status === 'PENDING').length,
      confirmed: reports.filter(r => r.status === 'CONFIRMED').length,
      rejected:  reports.filter(r => r.status === 'REJECTED').length,
      resolved:  reports.filter(r => r.status === 'RESOLVED').length,
      dualConfirmedPct: reports.length > 0
        ? Math.round(reports.filter(r => r.detectionSource === 'DUAL_CONFIRMED').length / reports.length * 100)
        : 0,
      avgFusedScore: reports.length > 0
        ? reports.reduce((s, r) => s + r.fusedScore, 0) / reports.length
        : 0,
    };

    // ── Priority Zones ───────────────────────────────────────────────────────
    const zoneMap = new Map<string, Report[]>();
    for (const r of reports) {
      if (!r.location) continue;
      const key = gridKey(r.location.latitude, r.location.longitude);
      if (!zoneMap.has(key)) zoneMap.set(key, []);
      zoneMap.get(key)!.push(r);
    }

    const priorityZones: PriorityZone[] = [];
    zoneMap.forEach((zReports, key) => {
      const avgSeverity = zReports.reduce((s, r) => s + r.fusedScore, 0) / zReports.length;
      const latestMs = zReports.reduce((m, r) => {
        const ts = r.timestamp?.toMillis?.() ?? 0;
        return Math.max(m, ts);
      }, 0);
      const daysSinceLatest = (now - latestMs) / 86_400_000;
      const priority = avgSeverity * Math.log2(zReports.length + 1) * decayFactor(daysSinceLatest);
      const [latS, lngS] = key.split(',');
      priorityZones.push({
        geohash: key,
        label: `${parseFloat(latS).toFixed(2)}°, ${parseFloat(lngS).toFixed(2)}°`,
        reportCount: zReports.length,
        confirmedCount: zReports.filter(r => r.status === 'CONFIRMED').length,
        avgSeverity,
        daysSinceLatest: Math.round(daysSinceLatest),
        priorityScore: priority,
      });
    });
    priorityZones.sort((a, b) => b.priorityScore - a.priorityScore);

    // ── Damage Clusters ──────────────────────────────────────────────────────
    const geoReports = reports.filter(r => r.location != null);
    const assigned = new Set<string>();
    const clusters: DamageCluster[] = [];

    for (const seed of geoReports) {
      if (assigned.has(seed.id)) continue;
      const members = geoReports.filter(r => {
        if (assigned.has(r.id)) return false;
        if (!seed.location || !r.location) return false;
        return haversine(
          seed.location.latitude, seed.location.longitude,
          r.location.latitude,    r.location.longitude
        ) <= CLUSTER_RADIUS_M;
      });

      if (members.length < 2) continue;
      members.forEach(r => assigned.add(r.id));

      const centroidLat = members.reduce((s, r) => s + r.location!.latitude, 0) / members.length;
      const centroidLng = members.reduce((s, r) => s + r.location!.longitude, 0) / members.length;
      const typeCount = members.reduce<Record<string, number>>((m, r) => {
        m[r.damageType] = (m[r.damageType] ?? 0) + 1;
        return m;
      }, {});
      const dominantType = Object.entries(typeCount).sort((a, b) => b[1] - a[1])[0]?.[0] ?? 'unknown';
      const maxRadius = Math.max(...members.map(r =>
        haversine(centroidLat, centroidLng, r.location!.latitude, r.location!.longitude)
      ));

      clusters.push({
        centroidLat,
        centroidLng,
        count: members.length,
        dominantType,
        avgFusedScore: members.reduce((s, r) => s + r.fusedScore, 0) / members.length,
        radiusM: Math.round(maxRadius),
      });
    }
    clusters.sort((a, b) => b.count - a.count);

    // ── Degradation Trend ────────────────────────────────────────────────────
    const monthLabels: string[] = [];
    const monthlyCounts: number[] = [];

    for (let i = TREND_MONTHS - 1; i >= 0; i--) {
      const d = new Date(now);
      d.setMonth(d.getMonth() - i);
      const label = d.toLocaleString('en-US', { month: 'short', year: 'numeric' });
      monthLabels.push(label);
      const count = reports.filter(r => {
        if (!r.timestamp) return false;
        const ts = r.timestamp.toMillis?.() ?? 0;
        const rd = new Date(ts);
        return rd.getFullYear() === d.getFullYear() && rd.getMonth() === d.getMonth();
      }).length;
      monthlyCounts.push(count);
    }

    const xs = monthlyCounts.map((_, i) => i);
    const slope = olsSlope(xs, monthlyCounts);
    const trend: TrendClassification =
      slope < -0.5 ? 'IMPROVING' : slope > 0.5 ? 'DEGRADING' : 'STABLE';

    const xMean = xs.reduce((a, b) => a + b, 0) / xs.length;
    const yMean = monthlyCounts.reduce((a, b) => a + b, 0) / monthlyCounts.length;
    const intercept = yMean - slope * xMean;
    const nextMonthPredicted = Math.max(0, Math.round(slope * TREND_MONTHS + intercept));

    const monthly: MonthlyPoint[] = monthLabels.map((label, i) => ({
      label,
      count: monthlyCounts[i],
    }));

    const forecast: DegradationForecast = { monthly, slope, trend, nextMonthPredicted };

    return {
      summary,
      priorityZones: priorityZones.slice(0, 5),
      clusters: clusters.slice(0, 5),
      forecast,
    };
  }, [reports]);
}
