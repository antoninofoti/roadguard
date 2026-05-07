/**
 * Summary statistics KPI cards.
 *
 * Mirrors the AnalyticsSummary from the Android app's PredictiveAnalytics.
 */

import {
  AlertTriangle,
  CheckCircle,
  Clock,
  FileCheck,
  BarChart,
  Zap,
  TrendingUp,
  XCircle,
} from "lucide-react";
import type { Report } from "../../types/report";

interface StatsCardsProps {
  reports: Report[];
}

export function StatsCards({ reports }: StatsCardsProps) {
  const total = reports.length;
  const pending = reports.filter((r) => r.status === "PENDING").length;
  const confirmed = reports.filter((r) => r.status === "CONFIRMED").length;
  const resolved = reports.filter((r) => r.status === "RESOLVED").length;
  const rejected = reports.filter((r) => r.status === "REJECTED").length;

  // Calculate severity statistics
  const avgSeverity =
    total > 0 ? reports.reduce((sum, r) => sum + r.severity, 0) / total : 0;

  const critical = reports.filter((r) => r.severity >= 0.75).length;
  const high = reports.filter(
    (r) => r.severity >= 0.5 && r.severity < 0.75,
  ).length;

  const avgFused =
    total > 0 ? reports.reduce((sum, r) => sum + r.fusedScore, 0) / total : 0;
  const dualPercent =
    total > 0
      ? (reports.filter((r) => r.detectionSource === "DUAL_CONFIRMED").length /
          total) *
        100
      : 0;

  const stats = [
    {
      label: "Total",
      value: total,
      icon: BarChart,
      color: "#22d3ee",
    },
    {
      label: "Pending",
      value: pending,
      icon: Clock,
      color: "#f59e0b",
    },
    {
      label: "Confirmed",
      value: confirmed,
      icon: AlertTriangle,
      color: "#3b82f6",
    },
    {
      label: "Resolved",
      value: resolved,
      icon: CheckCircle,
      color: "#22c55e",
    },
  ];

  return (
    <div className="space-y-2">
      {/* Grid of stat cards */}
      <div className="grid grid-cols-2 gap-2">
        {stats.map(({ label, value, icon: Icon, color }) => (
          <div
            key={label}
            className="p-2.5 rounded-lg bg-surface-900/60 border border-glass-border"
          >
            <div className="flex items-center gap-1.5 mb-1">
              <Icon className="w-3.5 h-3.5" style={{ color }} />
              <span className="text-[10px] text-slate-500 uppercase tracking-wider">
                {label}
              </span>
            </div>
            <span className="text-lg font-bold text-white font-mono">
              {value}
            </span>
          </div>
        ))}
      </div>

      {/* Severity breakdown row */}
      <div className="flex gap-2">
        <div className="flex-1 p-2 rounded-lg bg-surface-900/60 border border-glass-border">
          <div className="flex items-center gap-1.5">
            <Zap className="w-3 h-3 text-red-500" />
            <span className="text-[10px] text-slate-500">Critical</span>
          </div>
          <span className="text-sm font-bold font-mono text-red-400">
            {critical}
          </span>
        </div>
        <div className="flex-1 p-2 rounded-lg bg-surface-900/60 border border-glass-border">
          <div className="flex items-center gap-1.5">
            <TrendingUp className="w-3 h-3 text-orange-500" />
            <span className="text-[10px] text-slate-500">High</span>
          </div>
          <span className="text-sm font-bold font-mono text-orange-400">
            {high}
          </span>
        </div>
      </div>

      {/* Average metrics row */}
      <div className="flex gap-2">
        <div className="flex-1 p-2 rounded-lg bg-surface-900/60 border border-glass-border">
          <div className="flex items-center gap-1.5">
            <FileCheck className="w-3 h-3 text-cyan-400" />
            <span className="text-[10px] text-slate-500">Avg Score</span>
          </div>
          <span className="text-sm font-bold font-mono text-white">
            {(avgFused * 100).toFixed(0)}%
          </span>
        </div>
        <div className="flex-1 p-2 rounded-lg bg-surface-900/60 border border-glass-border">
          <div className="flex items-center gap-1.5">
            <TrendingUp className="w-3 h-3 text-amber-400" />
            <span className="text-[10px] text-slate-500">Avg Severity</span>
          </div>
          <span className="text-sm font-bold font-mono text-white">
            {(avgSeverity * 100).toFixed(0)}%
          </span>
        </div>
      </div>

      {/* Status breakdown row */}
      <div className="flex gap-2">
        <div className="flex-1 p-2 rounded-lg bg-surface-900/60 border border-glass-border">
          <div className="flex items-center gap-1.5">
            <XCircle className="w-3 h-3 text-slate-500" />
            <span className="text-[10px] text-slate-500">Rejected</span>
          </div>
          <span className="text-sm font-bold font-mono text-slate-400">
            {rejected}
          </span>
        </div>
        <div className="flex-1 p-2 rounded-lg bg-surface-900/60 border border-glass-border">
          <div className="flex items-center gap-1.5">
            <FileCheck className="w-3 h-3 text-emerald-400" />
            <span className="text-[10px] text-slate-500">Dual</span>
          </div>
          <span className="text-sm font-bold font-mono text-white">
            {dualPercent.toFixed(0)}%
          </span>
        </div>
      </div>
    </div>
  );
}
