/**
 * Navigation sidebar with filters and stats.
 */

import type { ReportFilters, Report } from "../../types/report";
import { StatusFilter } from "../filters/StatusFilter";
import { SeverityFilter } from "../filters/SeverityFilter";
import { DamageTypeFilter } from "../filters/DamageTypeFilter";
import { StatsCards } from "../dashboard/StatsCards";
import { Map, Filter, BarChart3, X } from "lucide-react";

interface SidebarProps {
  filters: ReportFilters;
  onFiltersChange: (filters: ReportFilters) => void;
  reports: Report[];
  isOpen: boolean;
  onClose: () => void;
}

export function Sidebar({
  filters,
  onFiltersChange,
  reports,
  isOpen,
  onClose,
}: SidebarProps) {
  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={`
          fixed lg:static inset-y-0 left-0 z-50
          w-80 bg-surface-800 border-r border-glass-border
          flex flex-col overflow-y-auto
          transition-transform duration-300 ease-in-out
          ${isOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"}
        `}
      >
        {/* Mobile close button */}
        <div className="flex items-center justify-between p-4 lg:hidden">
          <span className="text-white font-semibold">Filters & Stats</span>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-surface-700 text-slate-400"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Stats Section */}
        <div className="p-4 border-b border-glass-border">
          <div className="flex items-center gap-2 mb-3">
            <BarChart3 className="w-4 h-4 text-accent-primary" />
            <h2 className="text-sm font-semibold text-white">Overview</h2>
          </div>
          <StatsCards reports={reports} />
        </div>

        {/* Filters Section */}
        <div className="p-4 flex-1">
          <div className="flex items-center gap-2 mb-4">
            <Filter className="w-4 h-4 text-accent-primary" />
            <h2 className="text-sm font-semibold text-white">Filters</h2>
          </div>

          <div className="space-y-5">
            <StatusFilter
              selected={filters.statuses}
              onChange={(statuses) => onFiltersChange({ ...filters, statuses })}
            />

            <SeverityFilter
              min={filters.severityMin}
              max={filters.severityMax}
              onChange={(min, max) =>
                onFiltersChange({
                  ...filters,
                  severityMin: min,
                  severityMax: max,
                })
              }
            />

            <DamageTypeFilter
              selected={filters.damageTypes}
              onChange={(damageTypes) =>
                onFiltersChange({ ...filters, damageTypes })
              }
            />
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-glass-border">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <Map className="w-3.5 h-3.5" />
            <span>{reports.length} reports loaded</span>
          </div>
        </div>
      </aside>
    </>
  );
}
