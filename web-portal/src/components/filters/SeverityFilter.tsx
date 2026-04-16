/**
 * Severity range slider filter.
 */

import { getSeverityColor } from '../../types/report';

interface SeverityFilterProps {
  min: number;
  max: number;
  onChange: (min: number, max: number) => void;
}

export function SeverityFilter({ min, max, onChange }: SeverityFilterProps) {
  return (
    <div>
      <label className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2 block">
        Severity Range
      </label>

      <div className="space-y-3">
        {/* Min slider */}
        <div>
          <div className="flex justify-between text-xs text-slate-500 mb-1">
            <span>Min</span>
            <span
              className="font-mono font-medium"
              style={{ color: getSeverityColor(min) }}
            >
              {min.toFixed(2)}
            </span>
          </div>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={min}
            onChange={(e) => {
              const newMin = parseFloat(e.target.value);
              onChange(Math.min(newMin, max), max);
            }}
            className="w-full h-1.5 rounded-full appearance-none cursor-pointer"
            style={{
              background: `linear-gradient(to right, #22c55e, #eab308, #f97316, #ef4444)`,
            }}
          />
        </div>

        {/* Max slider */}
        <div>
          <div className="flex justify-between text-xs text-slate-500 mb-1">
            <span>Max</span>
            <span
              className="font-mono font-medium"
              style={{ color: getSeverityColor(max) }}
            >
              {max.toFixed(2)}
            </span>
          </div>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={max}
            onChange={(e) => {
              const newMax = parseFloat(e.target.value);
              onChange(min, Math.max(newMax, min));
            }}
            className="w-full h-1.5 rounded-full appearance-none cursor-pointer"
            style={{
              background: `linear-gradient(to right, #22c55e, #eab308, #f97316, #ef4444)`,
            }}
          />
        </div>
      </div>
    </div>
  );
}
