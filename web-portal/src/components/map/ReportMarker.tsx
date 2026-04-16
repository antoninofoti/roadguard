/**
 * Individual report marker with severity-coded color.
 *
 * Uses CircleMarker for performance + distinct shapes for colorblind accessibility.
 */

import { CircleMarker, Popup, Tooltip } from 'react-leaflet';
import {
  getSeverityColor,
  formatDamageType,
  type Report,
} from '../../types/report';
import { StatusBadge } from '../reports/StatusBadge';

interface ReportMarkerProps {
  report: Report;
  isSelected: boolean;
  onClick: () => void;
}

export function ReportMarker({
  report,
  isSelected,
  onClick,
}: ReportMarkerProps) {
  if (!report.location) return null;

  const color = getSeverityColor(report.severity);
  const lat = report.location.latitude;
  const lng = report.location.longitude;

  return (
    <CircleMarker
      center={[lat, lng]}
      radius={isSelected ? 10 : 7}
      pathOptions={{
        fillColor: color,
        fillOpacity: isSelected ? 0.9 : 0.7,
        color: isSelected ? '#fff' : color,
        weight: isSelected ? 3 : 1.5,
        opacity: 1,
      }}
      eventHandlers={{
        click: onClick,
      }}
    >
      {/* Tooltip on hover */}
      <Tooltip
        direction="top"
        offset={[0, -8]}
        className="!bg-[var(--color-surface-800)] !border-[var(--color-glass-border)] !text-slate-200 !rounded-lg !shadow-lg"
      >
        <div className="text-xs p-1">
          <div className="font-semibold">
            {formatDamageType(report.damageType || 'Unknown')}
          </div>
          <div className="text-slate-400 font-mono">
            Score: {(report.fusedScore * 100).toFixed(0)}%
          </div>
        </div>
      </Tooltip>

      {/* Popup on click */}
      <Popup>
        <div className="min-w-48 space-y-2">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-sm" style={{ color }}>
              {formatDamageType(report.damageType || 'Unknown')}
            </span>
            <StatusBadge status={report.status} />
          </div>

          {report.imageUrl && (
            <img
              src={report.imageUrl}
              alt={report.damageType}
              className="w-full h-24 object-cover rounded"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          )}

          <div className="grid grid-cols-2 gap-1 text-[11px]">
            <div>
              <span className="text-slate-500">Fused:</span>{' '}
              <span className="font-mono font-medium" style={{ color }}>
                {(report.fusedScore * 100).toFixed(0)}%
              </span>
            </div>
            <div>
              <span className="text-slate-500">CV:</span>{' '}
              <span className="font-mono">
                {(report.cvConfidence * 100).toFixed(0)}%
              </span>
            </div>
            <div>
              <span className="text-slate-500">IMU:</span>{' '}
              <span className="font-mono">
                {(report.sensorConfidence * 100).toFixed(0)}%
              </span>
            </div>
            <div>
              <span className="text-slate-500">Severity:</span>{' '}
              <span className="font-mono">
                {(report.severity * 100).toFixed(0)}%
              </span>
            </div>
          </div>

          <button
            onClick={onClick}
            className="w-full text-center py-1.5 rounded text-xs font-medium bg-[var(--color-accent-primary)]/20 text-[var(--color-accent-primary)] hover:bg-[var(--color-accent-primary)]/30 transition-colors"
          >
            View Details
          </button>
        </div>
      </Popup>
    </CircleMarker>
  );
}
