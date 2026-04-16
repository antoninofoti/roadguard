/**
 * Status filter toggle chips.
 */

import { REPORT_STATUSES, getStatusColor, type ReportStatus } from '../../types/report';

interface StatusFilterProps {
  selected: ReportStatus[];
  onChange: (statuses: ReportStatus[]) => void;
}

export function StatusFilter({ selected, onChange }: StatusFilterProps) {
  const toggle = (status: ReportStatus) => {
    if (selected.includes(status)) {
      onChange(selected.filter((s) => s !== status));
    } else {
      onChange([...selected, status]);
    }
  };

  return (
    <div>
      <label className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2 block">
        Status
      </label>
      <div className="flex flex-wrap gap-2">
        {REPORT_STATUSES.map((status) => {
          const isActive = selected.includes(status);
          const color = getStatusColor(status);

          return (
            <button
              key={status}
              onClick={() => toggle(status)}
              className="px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-200"
              style={{
                backgroundColor: isActive ? `${color}20` : 'transparent',
                color: isActive ? color : '#94a3b8',
                border: `1px solid ${isActive ? `${color}40` : 'rgba(255,255,255,0.08)'}`,
              }}
            >
              {status}
            </button>
          );
        })}
      </div>
    </div>
  );
}
