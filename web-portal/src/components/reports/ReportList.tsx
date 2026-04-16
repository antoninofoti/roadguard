/**
 * Scrollable report list panel.
 */

import { FileWarning } from 'lucide-react';
import { ReportCard } from './ReportCard';
import type { Report } from '../../types/report';

interface ReportListProps {
  reports: Report[];
  selectedId: string | null;
  onSelect: (report: Report) => void;
  isLoading: boolean;
}

export function ReportList({
  reports,
  selectedId,
  onSelect,
  isLoading,
}: ReportListProps) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-2 p-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <div
            key={i}
            className="h-20 rounded-lg bg-[var(--color-surface-700)] animate-pulse"
          />
        ))}
      </div>
    );
  }

  if (reports.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 px-4 text-center">
        <FileWarning className="w-10 h-10 text-slate-600 mb-3" />
        <p className="text-sm text-slate-400">No reports match your filters</p>
        <p className="text-xs text-slate-500 mt-1">
          Try adjusting your filter criteria
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1 p-2 overflow-y-auto max-h-[calc(100vh-18rem)]">
      {reports.map((report) => (
        <ReportCard
          key={report.id}
          report={report}
          isSelected={report.id === selectedId}
          onClick={() => onSelect(report)}
        />
      ))}
    </div>
  );
}
