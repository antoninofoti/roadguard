/**
 * Compact report card for the report list.
 */

import { MapPin, Clock, Zap } from 'lucide-react';
import { StatusBadge } from './StatusBadge';
import {
  getSeverityColor,
  formatDamageType,
  formatDetectionSource,
  type Report,
} from '../../types/report';

interface ReportCardProps {
  report: Report;
  isSelected: boolean;
  onClick: () => void;
}

export function ReportCard({ report, isSelected, onClick }: ReportCardProps) {
  const severityColor = getSeverityColor(report.severity);
  const timestamp = report.timestamp?.toDate();

  return (
    <button
      onClick={onClick}
      className={`
        w-full text-left p-3 rounded-lg transition-all duration-200 border
        ${
          isSelected
            ? 'bg-[var(--color-accent-primary)]/8 border-[var(--color-accent-primary)]/30'
            : 'bg-transparent border-transparent hover:bg-[var(--color-surface-700)] hover:border-[var(--color-glass-border)]'
        }
      `}
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          {/* Severity dot */}
          <span
            className="w-2.5 h-2.5 rounded-full shrink-0"
            style={{ backgroundColor: severityColor }}
          />
          <span className="text-sm font-medium text-white truncate">
            {formatDamageType(report.damageType || 'Unknown')}
          </span>
        </div>
        <StatusBadge status={report.status} />
      </div>

      <div className="flex items-center gap-3 text-xs text-slate-400">
        {/* Fused score */}
        <span
          className="flex items-center gap-1 font-mono font-medium"
          style={{ color: severityColor }}
        >
          <Zap className="w-3 h-3" />
          {(report.fusedScore * 100).toFixed(0)}%
        </span>

        {/* Detection source */}
        <span className="flex items-center gap-1">
          {formatDetectionSource(report.detectionSource)}
        </span>

        {/* Location */}
        {report.location && (
          <span className="flex items-center gap-1">
            <MapPin className="w-3 h-3" />
            {report.location.latitude.toFixed(3)},{' '}
            {report.location.longitude.toFixed(3)}
          </span>
        )}
      </div>

      {/* Timestamp */}
      {timestamp && (
        <div className="flex items-center gap-1 mt-1.5 text-[11px] text-slate-500">
          <Clock className="w-3 h-3" />
          {timestamp.toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
          })}
        </div>
      )}
    </button>
  );
}
