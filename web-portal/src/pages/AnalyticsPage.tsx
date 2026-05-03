/**
 * AnalyticsPage — Municipal Operator Analytics Dashboard.
 *
 * Web equivalent of the Android AnalyticsScreen.kt.
 * Displays predictive maintenance data computed client-side from Firestore reports:
 *
 *  Row 1: Summary Cards (6 KPIs)
 *  Row 2: 6-month trend chart + trend badge
 *  Row 3: Top-5 Priority Zones  |  Top-5 Damage Clusters
 *
 * All data is derived via useAnalytics() which mirrors PredictiveAnalytics.kt logic.
 */

import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import {
  TrendingUp, TrendingDown, Minus, MapPin, Layers,
  AlertTriangle, CheckCircle, Clock, Activity, Target, Zap,
  Flame, LayoutGrid
} from 'lucide-react';
import { Header } from '../components/layout/Header';
import { ReportMap } from '../components/map/ReportMap';
import { useReports } from '../hooks/useReports';
import { useAnalytics, type TrendClassification } from '../hooks/useAnalytics';
import { DEFAULT_FILTERS } from '../types/report';

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Normalise a value in [0, max] to a CSS width % string. */
function barWidth(val: number, max: number): string {
  return max === 0 ? '0%' : `${Math.min(100, (val / max) * 100).toFixed(1)}%`;
}

/** Colour for a priority score (0 … ~1). */
function priorityColor(score: number): string {
  if (score >= 0.6) return 'text-red-400';
  if (score >= 0.3) return 'text-amber-400';
  return 'text-emerald-400';
}

/** Trend badge colour + icon. */
function trendMeta(trend: TrendClassification) {
  switch (trend) {
    case 'IMPROVING':  return { color: 'text-emerald-400 bg-emerald-400/10', Icon: TrendingDown,  label: 'IMPROVING' };
    case 'DEGRADING':  return { color: 'text-red-400    bg-red-400/10',      Icon: TrendingUp,    label: 'DEGRADING' };
    default:           return { color: 'text-amber-400  bg-amber-400/10',    Icon: Minus,         label: 'STABLE' };
  }
}

/** Damage type → display label. */
function damageLabel(type: string): string {
  return type.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()) || 'Unknown';
}

// ── Mini line chart (pure SVG, no external lib) ──────────────────────────────
function TrendChart({ points, slope }: { points: { label: string; count: number }[]; slope: number }) {
  const max = Math.max(...points.map(p => p.count), 1);
  const w = 600, h = 140, pad = 30;
  const xStep = (w - 2 * pad) / (points.length - 1);

  const coords = points.map((p, i) => ({
    x: pad + i * xStep,
    y: h - pad - ((p.count / max) * (h - 2 * pad)),
  }));

  const polyline = coords.map(c => `${c.x},${c.y}`).join(' ');
  // Trend regression line
  const yMean = h - pad - (points.reduce((s, p) => s + p.count, 0) / points.length / max) * (h - 2 * pad);
  const x0 = pad, x1 = w - pad;
  const halfSpan = (x1 - x0) / 2;
  const y0 = yMean + slope * (-halfSpan / xStep) * ((h - 2 * pad) / max);
  const y1 = yMean + slope * (halfSpan  / xStep) * ((h - 2 * pad) / max);

  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="w-full h-36" aria-label="Monthly report trend chart">
      {/* Grid lines */}
      {[0, 0.25, 0.5, 0.75, 1].map(f => {
        const cy = pad + (1 - f) * (h - 2 * pad);
        return (
          <line key={f} x1={pad} y1={cy} x2={w - pad} y2={cy}
            stroke="rgba(255,255,255,0.07)" strokeWidth="1" />
        );
      })}
      {/* Trend line */}
      <line x1={x0} y1={Math.max(pad, Math.min(h - pad, y0))}
            x2={x1} y2={Math.max(pad, Math.min(h - pad, y1))}
        stroke="rgba(99,102,241,0.5)" strokeWidth="1.5" strokeDasharray="4 3" />
      {/* Data line */}
      <polyline points={polyline} fill="none" stroke="#22d3ee" strokeWidth="2.5"
        strokeLinejoin="round" strokeLinecap="round" />
      {/* Dots */}
      {coords.map((c, i) => (
        <circle key={i} cx={c.x} cy={c.y} r={4} fill="#22d3ee" />
      ))}
      {/* X labels */}
      {points.map((p, i) => (
        <text key={i} x={pad + i * xStep} y={h - 6} textAnchor="middle"
          fontSize="9" fill="rgba(255,255,255,0.4)">
          {p.label.split(' ')[0]}
        </text>
      ))}
    </svg>
  );
}

// ── Summary Card ─────────────────────────────────────────────────────────────
interface SummaryCardProps {
  label: string;
  value: string | number;
  sub?: string;
  Icon: React.ElementType;
  accent: string;
}
function SummaryCard({ label, value, sub, Icon, accent }: SummaryCardProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-[var(--color-surface-800)] border border-[var(--color-glass-border)] rounded-xl p-4 flex items-start gap-3"
    >
      <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${accent}`}>
        <Icon className="w-4.5 h-4.5" />
      </div>
      <div className="min-w-0">
        <p className="text-xs text-slate-500 uppercase tracking-wide font-medium">{label}</p>
        <p className="text-2xl font-bold text-white leading-tight">{value}</p>
        {sub && <p className="text-xs text-slate-500 mt-0.5">{sub}</p>}
      </div>
    </motion.div>
  );
}

// ── Main Component ───────────────────────────────────────────────────────────
export function AnalyticsPage() {
  // Fetch ALL statuses for analytics
  const allFilters = useMemo(
    () => ({ ...DEFAULT_FILTERS, statuses: ['PENDING', 'CONFIRMED', 'REJECTED', 'RESOLVED'] as Array<typeof import('../types/report').REPORT_STATUSES[number]> }),
    []
  );
  const { reports, isLoading } = useReports(allFilters as Parameters<typeof useReports>[0]);
  const analytics = useAnalytics(reports);
  const [showHeatmap, setShowHeatmap] = useState(true);
  const [selectedReportId, setSelectedReportId] = useState<string | null>(null);

  const { summary, priorityZones, clusters, forecast } = analytics;
  const trend = trendMeta(forecast.trend);
  const TrendIcon = trend.Icon;
  const maxPriority = priorityZones[0]?.priorityScore ?? 1;

  return (
    <div className="h-screen flex flex-col bg-[var(--color-surface-900)] overflow-hidden">
      <Header />

      <main className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-4 py-6 space-y-6">

          {/* Page title */}
          <div>
            <h2 className="text-xl font-semibold text-white">Predictive Analytics</h2>
            <p className="text-sm text-slate-500 mt-0.5">
              Data-driven maintenance intelligence · {reports.length} reports indexed
            </p>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center h-60 text-slate-500 text-sm">
              Loading analytics…
            </div>
          ) : (
            <>
              {/* ── Row 1: Summary Cards ─────────────────────────────────── */}
              <section aria-label="Summary statistics">
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                  <SummaryCard label="Total Reports" value={summary.total}
                    Icon={Activity} accent="bg-indigo-500/15 text-indigo-400" />
                  <SummaryCard label="Avg Fused Score"
                    value={`${(summary.avgFusedScore * 100).toFixed(0)}%`}
                    Icon={Target} accent="bg-cyan-500/15 text-cyan-400" />
                  <SummaryCard label="Pending" value={summary.pending}
                    Icon={Clock} accent="bg-amber-500/15 text-amber-400" />
                  <SummaryCard label="Confirmed" value={summary.confirmed}
                    Icon={CheckCircle} accent="bg-emerald-500/15 text-emerald-400" />
                  <SummaryCard label="Resolved" value={summary.resolved}
                    Icon={Zap} accent="bg-sky-500/15 text-sky-400" />
                  <SummaryCard label="Dual Confirmed" value={`${summary.dualConfirmedPct}%`}
                    sub="of all detections"
                    Icon={AlertTriangle} accent="bg-violet-500/15 text-violet-400" />
                </div>
              </section>

              {/* ── Row 2: Trend + Map ──────────────────────────────────── */}
              <section className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Condition Trend */}
                <div className="lg:col-span-1 bg-[var(--color-surface-800)] border border-[var(--color-glass-border)] rounded-xl p-5">
                  <div className="flex items-center justify-between mb-4">
                    <div>
                      <h3 className="text-sm font-semibold text-white">Road Condition Trend</h3>
                      <p className="text-xs text-slate-500">OLS Regression Analysis</p>
                    </div>
                    <div className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold ${trend.color}`}>
                      <TrendIcon className="w-3.5 h-3.5" />
                      {trend.label}
                    </div>
                  </div>
                  <TrendChart points={forecast.monthly} slope={forecast.slope} />
                  <div className="space-y-2 mt-4 pt-3 border-t border-[var(--color-glass-border)]">
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-slate-500">Regression Slope</span>
                      <span className="text-white font-mono">{forecast.slope.toFixed(3)}</span>
                    </div>
                    <div className="flex justify-between items-center text-xs">
                      <span className="text-slate-500">Next Month Prediction</span>
                      <span className="text-white font-bold">{forecast.nextMonthPredicted} reports</span>
                    </div>
                  </div>
                </div>

                {/* Spatial Insights Map */}
                <div className="lg:col-span-2 bg-[var(--color-surface-800)] border border-[var(--color-glass-border)] rounded-xl p-5 relative overflow-hidden group">
                  <div className="absolute top-8 right-8 z-[10] flex gap-2">
                    <button
                      onClick={() => setShowHeatmap(false)}
                      className={`p-2 rounded-lg backdrop-blur-md transition-all ${!showHeatmap ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/20' : 'bg-white/5 text-slate-400 hover:bg-white/10'}`}
                      title="Marker View"
                    >
                      <LayoutGrid className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => setShowHeatmap(true)}
                      className={`p-2 rounded-lg backdrop-blur-md transition-all ${showHeatmap ? 'bg-orange-500 text-white shadow-lg shadow-orange-500/20' : 'bg-white/5 text-slate-400 hover:bg-white/10'}`}
                      title="Heatmap View"
                    >
                      <Flame className="w-4 h-4" />
                    </button>
                  </div>

                  <div className="flex items-center gap-2 mb-4">
                    <MapPin className="w-4 h-4 text-cyan-400" />
                    <h3 className="text-sm font-semibold text-white">Spatial Intelligence</h3>
                  </div>

                  <div className="h-[240px] rounded-lg overflow-hidden border border-white/5">
                    <ReportMap
                      reports={reports}
                      selectedId={selectedReportId}
                      onSelect={(r) => setSelectedReportId(r.id)}
                      showHeatmap={showHeatmap}
                    />
                  </div>
                </div>
              </section>

              {/* ── Row 3: Zones + Clusters ──────────────────────────────── */}
              <section aria-label="Priority zones and damage clusters">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

                  {/* Priority Zones */}
                  <div className="bg-[var(--color-surface-800)] border border-[var(--color-glass-border)] rounded-xl p-5">
                    <div className="flex items-center gap-2 mb-4">
                      <MapPin className="w-4 h-4 text-red-400" />
                      <h3 className="text-sm font-semibold text-white">Top Priority Zones</h3>
                    </div>
                    {priorityZones.length === 0 ? (
                      <p className="text-xs text-slate-500 text-center py-8">No geolocated reports yet</p>
                    ) : (
                      <div className="space-y-3">
                        {priorityZones.map((z, i) => (
                          <motion.div key={z.geohash}
                            initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: i * 0.05 }}
                            className="flex items-start gap-3"
                          >
                            <span className="text-xs text-slate-600 font-mono w-4 shrink-0 mt-0.5">
                              {i + 1}
                            </span>
                            <div className="flex-1 min-w-0">
                              <div className="flex items-center justify-between gap-2">
                                <span className="text-xs text-slate-300 font-mono truncate">{z.label}</span>
                                <span className={`text-xs font-bold shrink-0 ${priorityColor(z.priorityScore)}`}>
                                  {z.priorityScore.toFixed(3)}
                                </span>
                              </div>
                              {/* Priority bar */}
                              <div className="mt-1 h-1 rounded-full bg-[var(--color-surface-700)]">
                                <div className="h-1 rounded-full bg-gradient-to-r from-cyan-500 to-indigo-500 transition-all"
                                  style={{ width: barWidth(z.priorityScore, maxPriority) }} />
                              </div>
                              <div className="flex gap-3 mt-1 text-xs text-slate-500">
                                <span>{z.reportCount} reports</span>
                                <span>{z.confirmedCount} confirmed</span>
                                <span>{z.daysSinceLatest}d ago</span>
                              </div>
                            </div>
                          </motion.div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Damage Clusters */}
                  <div className="bg-[var(--color-surface-800)] border border-[var(--color-glass-border)] rounded-xl p-5">
                    <div className="flex items-center gap-2 mb-4">
                      <Layers className="w-4 h-4 text-violet-400" />
                      <h3 className="text-sm font-semibold text-white">Top Damage Clusters</h3>
                    </div>
                    {clusters.length === 0 ? (
                      <p className="text-xs text-slate-500 text-center py-8">
                        Not enough geolocated reports within 100 m proximity
                      </p>
                    ) : (
                      <div className="space-y-3">
                        {clusters.map((c, i) => (
                          <motion.div key={i}
                            initial={{ opacity: 0, x: 8 }} animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: i * 0.05 }}
                            className="bg-[var(--color-surface-700)]/50 rounded-lg p-3 border border-[var(--color-glass-border)]"
                          >
                            <div className="flex items-center justify-between">
                              <div className="flex items-center gap-2">
                                <span className="w-6 h-6 rounded-full bg-violet-500/20 text-violet-400 text-xs font-bold flex items-center justify-center">
                                  {c.count}
                                </span>
                                <span className="text-sm text-white font-medium">
                                  {damageLabel(c.dominantType)}
                                </span>
                              </div>
                              <span className="text-xs text-slate-400 font-mono">
                                {(c.avgFusedScore * 100).toFixed(0)}% fused
                              </span>
                            </div>
                            <div className="flex gap-3 mt-2 text-xs text-slate-500">
                              <span>r ≈ {c.radiusM} m</span>
                              <span>
                                {c.centroidLat.toFixed(4)}°, {c.centroidLng.toFixed(4)}°
                              </span>
                            </div>
                          </motion.div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </section>
            </>
          )}
        </div>
      </main>
    </div>
  );
}
