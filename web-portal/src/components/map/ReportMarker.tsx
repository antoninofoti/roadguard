/**
 * Individual report marker with severity-coded color.
 *
 * Uses custom Marker with icon styling for performance.
 */

import { Marker, Popup, Tooltip } from "react-leaflet";
import L from "leaflet";
import {
  getSeverityColor,
  formatDamageType,
  type Report,
} from "../../types/report";
import { StatusBadge } from "../reports/StatusBadge";

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
  // Use a divIcon to emulate a colored circle marker (avoids CircleMarker export issues)
  const diameter = isSelected ? 16 : 12;
  const html = `<div style="width:${diameter}px;height:${diameter}px;border-radius:50%;background:${color};border:${isSelected ? "2px solid #ffffff" : "1px solid rgba(0,0,0,0.15)"};box-shadow:0 2px 6px rgba(0,0,0,0.35);"></div>`;

  const icon = L.divIcon({
    html,
    className: "",
    iconSize: L.point(diameter, diameter),
    iconAnchor: L.point(diameter / 2, diameter / 2),
  });

  return (
    <Marker
      position={[lat, lng]}
      icon={icon}
      eventHandlers={{ click: onClick }}
    >
      <Tooltip
        direction="top"
        offset={[0, -Math.round(diameter / 2) - 6]}
        className="bg-surface-800! border-glass-border! text-slate-200! rounded-lg! shadow-lg!"
      >
        <div className="text-xs p-1">
          <div className="font-semibold">
            {formatDamageType(report.damageType || "Unknown")}
          </div>
          <div className="text-slate-400 font-mono">
            Score: {(report.fusedScore * 100).toFixed(0)}%
          </div>
        </div>
      </Tooltip>

      <Popup>
        <div className="min-w-48 space-y-2">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-sm" style={{ color }}>
              {formatDamageType(report.damageType || "Unknown")}
            </span>
            <StatusBadge status={report.status} />
          </div>

          {report.imageUrl && (
            <img
              src={report.imageUrl}
              alt={report.damageType}
              className="w-full h-24 object-cover rounded"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = "none";
              }}
            />
          )}

          <div className="grid grid-cols-2 gap-1 text-[11px]">
            <div>
              <span className="text-slate-500">Fused:</span>{" "}
              <span className="font-mono font-medium" style={{ color }}>
                {(report.fusedScore * 100).toFixed(0)}%
              </span>
            </div>
            <div>
              <span className="text-slate-500">CV:</span>{" "}
              <span className="font-mono">
                {(report.cvConfidence * 100).toFixed(0)}%
              </span>
            </div>
            <div>
              <span className="text-slate-500">IMU:</span>{" "}
              <span className="font-mono">
                {(report.sensorConfidence * 100).toFixed(0)}%
              </span>
            </div>
            <div>
              <span className="text-slate-500">Severity:</span>{" "}
              <span className="font-mono">
                {(report.severity * 100).toFixed(0)}%
              </span>
            </div>
          </div>

          <button
            onClick={onClick}
            className="w-full text-center py-1.5 rounded text-xs font-medium bg-accent-primary/20 text-accent-primary hover:bg-accent-primary/30 transition-colors"
          >
            View Details
          </button>
        </div>
      </Popup>
    </Marker>
  );
}
