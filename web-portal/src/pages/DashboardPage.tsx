/**
 * Main dashboard page — the operator's command center.
 *
 * Three-panel layout:
 * - Left: Sidebar with filters + stats
 * - Center: Leaflet map (full height)
 * - Right: Report detail panel (slides in on selection)
 *
 * Below the map: scrollable report list
 */

import { useState, useCallback } from 'react';
import { lazy, Suspense } from 'react';
import { motion } from 'framer-motion';
import { Menu, List, MapIcon } from 'lucide-react';
import { Header } from '../components/layout/Header';
import { Sidebar } from '../components/layout/Sidebar';
import { ReportList } from '../components/reports/ReportList';
import { useReports } from '../hooks/useReports';
import { DEFAULT_FILTERS, type Report, type ReportFilters } from '../types/report';

const ReportMap = lazy(() =>
  import('../components/map/ReportMap').then((module) => ({
    default: module.ReportMap,
  }))
);
const ReportDetail = lazy(() =>
  import('../components/reports/ReportDetail').then((module) => ({
    default: module.ReportDetail,
  }))
);

export function DashboardPage() {
  const [filters, setFilters] = useState<ReportFilters>(DEFAULT_FILTERS);
  const [selectedReport, setSelectedReport] = useState<Report | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [activeView, setActiveView] = useState<'map' | 'list'>('map');

  const { reports, isLoading, error } = useReports(filters);

  const handleSelectReport = useCallback((report: Report) => {
    setSelectedReport(report);
  }, []);

  const handleCloseDetail = useCallback(() => {
    setSelectedReport(null);
  }, []);

  return (
    <div className="h-screen flex flex-col bg-[var(--color-surface-900)]">
      <Header />

      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <Sidebar
          filters={filters}
          onFiltersChange={setFilters}
          reports={reports}
          isOpen={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
        />

        {/* Main Content Area */}
        <main className="flex-1 flex flex-col overflow-hidden">
          {/* Toolbar */}
          <div className="h-12 bg-[var(--color-surface-800)] border-b border-[var(--color-glass-border)] flex items-center justify-between px-4 shrink-0">
            <div className="flex items-center gap-3">
              {/* Mobile sidebar toggle */}
              <button
                onClick={() => setSidebarOpen(true)}
                className="lg:hidden p-1.5 rounded-lg hover:bg-[var(--color-surface-700)] text-slate-400"
              >
                <Menu className="w-5 h-5" />
              </button>

              <h2 className="text-sm font-medium text-white">
                Road Damage Reports
              </h2>

              {error && (
                <span className="text-xs text-red-400 bg-red-400/10 px-2 py-0.5 rounded">
                  {error}
                </span>
              )}
            </div>

            {/* View toggle (mobile) */}
            <div className="flex items-center gap-1 bg-[var(--color-surface-900)] rounded-lg p-0.5">
              <button
                onClick={() => setActiveView('map')}
                className={`p-1.5 rounded-md text-xs transition-colors ${
                  activeView === 'map'
                    ? 'bg-[var(--color-surface-700)] text-white'
                    : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                <MapIcon className="w-4 h-4" />
              </button>
              <button
                onClick={() => setActiveView('list')}
                className={`p-1.5 rounded-md text-xs transition-colors ${
                  activeView === 'list'
                    ? 'bg-[var(--color-surface-700)] text-white'
                    : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                <List className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Content */}
          <div className="flex-1 flex overflow-hidden">
            {/* Map + List split */}
            <div className="flex-1 flex flex-col lg:flex-row overflow-hidden">
              {/* Map */}
              <motion.div
                className={`flex-1 p-3 ${
                  activeView !== 'map' ? 'hidden lg:block' : ''
                }`}
                layout
              >
                <div className="w-full h-full rounded-xl overflow-hidden border border-[var(--color-glass-border)] shadow-lg">
                  <Suspense
                    fallback={
                      <div className="w-full h-full flex items-center justify-center bg-[var(--color-surface-900)] text-slate-400 text-sm">
                        Loading map module...
                      </div>
                    }
                  >
                    <ReportMap
                      reports={reports}
                      selectedId={selectedReport?.id ?? null}
                      onSelect={handleSelectReport}
                    />
                  </Suspense>
                </div>
              </motion.div>

              {/* Report List panel */}
              <div
                className={`w-full lg:w-80 bg-[var(--color-surface-800)] border-l border-[var(--color-glass-border)] overflow-hidden ${
                  activeView !== 'list' ? 'hidden lg:block' : ''
                }`}
              >
                <div className="p-3 pb-2 border-b border-[var(--color-glass-border)]">
                  <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Reports ({reports.length})
                  </h3>
                </div>
                <ReportList
                  reports={reports}
                  selectedId={selectedReport?.id ?? null}
                  onSelect={handleSelectReport}
                  isLoading={isLoading}
                />
              </div>
            </div>
          </div>
        </main>

        {/* Detail panel (slides in from right) */}
        <Suspense fallback={null}>
          <ReportDetail report={selectedReport} onClose={handleCloseDetail} />
        </Suspense>
      </div>
    </div>
  );
}
