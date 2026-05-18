/**
 * Full report detail panel with operator workflow actions.
 *
 * Mirrors the Android OperatorReportDetailScreen functionality.
 */

import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  X,
  CheckCircle2,
  XCircle,
  Wrench,
  MapPin,
  Eye,
  Cpu,
  Zap,
  Clock,
  User,
  MessageSquare,
  Image,
} from 'lucide-react';
import { StatusBadge } from './StatusBadge';
import {
  getSeverityColor,
  getSeverityLevel,
  formatDamageType,
  formatDetectionSource,
  type Report,
} from '../../types/report';
import { useReportActions } from '../../hooks/useReportActions';

interface ReportDetailProps {
  report: Report | null;
  onClose: () => void;
}

export function ReportDetail({ report, onClose }: ReportDetailProps) {
  const { confirmReport, rejectReport, resolveReport, isUpdating } =
    useReportActions();
  const [notes, setNotes] = useState('');
  const [showImage, setShowImage] = useState(false);

  const handleAction = async (
    action: (id: string, notes?: string) => Promise<void>
  ) => {
    if (!report) return;
    try {
      await action(report.id, notes);
      setNotes('');
      onClose();
    } catch {
      // Error is handled by the hook
    }
  };

  return (
    <AnimatePresence>
      {report && (
        <motion.div
          initial={{ x: '100%', opacity: 0 }}
          animate={{ x: 0, opacity: 1 }}
          exit={{ x: '100%', opacity: 0 }}
          transition={{ type: 'spring', damping: 25, stiffness: 200 }}
          className="fixed right-0 top-16 bottom-0 w-96 bg-[var(--color-surface-800)] border-l border-[var(--color-glass-border)] z-[1001] overflow-y-auto shadow-2xl"
        >
          {/* Header */}
          <div className="sticky top-0 bg-[var(--color-surface-800)]/95 backdrop-blur-sm border-b border-[var(--color-glass-border)] p-4 flex items-center justify-between z-10">
            <div>
              <h3 className="text-white font-semibold">Report Detail</h3>
              <p className="text-xs text-slate-500 font-mono mt-0.5">
                {report.id.slice(0, 12)}...
              </p>
            </div>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg hover:bg-[var(--color-surface-700)] text-slate-400 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <div className="p-4 space-y-5">
            {/* Status + Damage Type */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span
                  className="w-3 h-3 rounded-full"
                  style={{
                    backgroundColor: getSeverityColor(report.severity),
                  }}
                />
                <span className="text-white font-medium">
                  {formatDamageType(report.damageType || 'Unknown')}
                </span>
              </div>
              <StatusBadge status={report.status} size="md" />
            </div>

            {/* Image Preview */}
            {report.imageUrl && (
              <div className="relative rounded-lg overflow-hidden border border-[var(--color-glass-border)]">
                <img
                  src={report.imageUrl}
                  alt={`Road damage: ${report.damageType}`}
                  className="w-full h-48 object-cover cursor-pointer hover:opacity-90 transition-opacity"
                  onClick={() => setShowImage(!showImage)}
                  onError={(e) => {
                    (e.target as HTMLImageElement).style.display = 'none';
                  }}
                />
                <div className="absolute top-2 right-2 p-1.5 rounded-md bg-black/50 text-white">
                  <Image className="w-4 h-4" />
                </div>
              </div>
            )}

            {/* Fusion Scores */}
            <div className="glass-card p-4 space-y-3">
              <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Fusion Analysis
              </h4>

              {/* Fused Score (main) */}
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-2 text-sm text-slate-300">
                  <Zap className="w-4 h-4 text-[var(--color-accent-primary)]" />
                  Fused Score
                </span>
                <span
                  className="text-lg font-bold font-mono"
                  style={{
                    color: getSeverityColor(report.fusedScore),
                  }}
                >
                  {(report.fusedScore * 100).toFixed(1)}%
                </span>
              </div>

              {/* Score Bar */}
              <div className="h-2 rounded-full bg-[var(--color-surface-700)] overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-500"
                  style={{
                    width: `${report.fusedScore * 100}%`,
                    background: `linear-gradient(90deg, ${getSeverityColor(0)}, ${getSeverityColor(report.fusedScore)})`,
                  }}
                />
              </div>

              {/* Sub-scores */}
              <div className="grid grid-cols-2 gap-3 pt-1">
                <div className="text-center p-2 rounded-lg bg-[var(--color-surface-900)]/50">
                  <Eye className="w-4 h-4 text-blue-400 mx-auto mb-1" />
                  <span className="text-xs text-slate-500 block">CV</span>
                  <span className="text-sm font-mono font-medium text-slate-200">
                    {(report.cvConfidence * 100).toFixed(0)}%
                  </span>
                </div>
                <div className="text-center p-2 rounded-lg bg-[var(--color-surface-900)]/50">
                  <Cpu className="w-4 h-4 text-orange-400 mx-auto mb-1" />
                  <span className="text-xs text-slate-500 block">IMU</span>
                  <span className="text-sm font-mono font-medium text-slate-200">
                    {(report.sensorConfidence * 100).toFixed(0)}%
                  </span>
                </div>
              </div>

              {/* Severity level */}
              <div className="flex items-center justify-between pt-1 text-xs">
                <span className="text-slate-500">Severity Level</span>
                <span
                  className="font-semibold uppercase"
                  style={{ color: getSeverityColor(report.severity) }}
                >
                  {getSeverityLevel(report.severity)}
                </span>
              </div>
            </div>

            {/* Metadata */}
            <div className="glass-card p-4 space-y-2.5">
              <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">
                Metadata
              </h4>

              <MetaRow
                icon={<MapPin className="w-3.5 h-3.5" />}
                label="Location"
                value={
                  report.location
                    ? `${report.location.latitude.toFixed(5)}, ${report.location.longitude.toFixed(5)}`
                    : 'N/A'
                }
              />

              <MetaRow
                icon={<Zap className="w-3.5 h-3.5" />}
                label="Source"
                value={formatDetectionSource(report.detectionSource)}
              />

              <MetaRow
                icon={<Clock className="w-3.5 h-3.5" />}
                label="Reported"
                value={
                  report.timestamp
                    ? report.timestamp.toDate().toLocaleString('en-GB')
                    : 'N/A'
                }
              />

              <MetaRow
                icon={<User className="w-3.5 h-3.5" />}
                label="Reporter"
                value={report.userId.slice(0, 12) + '...'}
              />

              {report.operatorId && (
                <MetaRow
                  icon={<User className="w-3.5 h-3.5" />}
                  label="Operator"
                  value={report.operatorId.slice(0, 12) + '...'}
                />
              )}

              {report.notes && (
                <MetaRow
                  icon={<MessageSquare className="w-3.5 h-3.5" />}
                  label="Notes"
                  value={report.notes}
                />
              )}
            </div>

            {/* Operator Actions */}
            {report.status === 'PENDING' && (
              <div className="space-y-3">
                <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Operator Actions
                </h4>

                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Add operator notes (optional)..."
                  rows={2}
                  className="w-full px-3 py-2 rounded-lg bg-[var(--color-surface-900)] border border-[var(--color-glass-border)] text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-[var(--color-accent-primary)]/50 resize-none"
                />

                <div className="flex gap-2">
                  <button
                    onClick={() => handleAction(confirmReport)}
                    disabled={isUpdating}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-lg text-sm font-medium bg-blue-500/15 text-blue-400 border border-blue-500/30 hover:bg-blue-500/25 transition-colors disabled:opacity-50"
                  >
                    <CheckCircle2 className="w-4 h-4" />
                    Confirm
                  </button>

                  <button
                    onClick={() => handleAction(rejectReport)}
                    disabled={isUpdating}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-lg text-sm font-medium bg-red-500/15 text-red-400 border border-red-500/30 hover:bg-red-500/25 transition-colors disabled:opacity-50"
                  >
                    <XCircle className="w-4 h-4" />
                    Reject
                  </button>
                </div>
              </div>
            )}

            {report.status === 'CONFIRMED' && (
              <div className="space-y-3">
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Resolution notes..."
                  rows={2}
                  className="w-full px-3 py-2 rounded-lg bg-[var(--color-surface-900)] border border-[var(--color-glass-border)] text-sm text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-[var(--color-accent-primary)]/50 resize-none"
                />

                <button
                  onClick={() => handleAction(resolveReport)}
                  disabled={isUpdating}
                  className="w-full flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-lg text-sm font-medium bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/25 transition-colors disabled:opacity-50"
                >
                  <Wrench className="w-4 h-4" />
                  Mark as Resolved
                </button>
              </div>
            )}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/** Small metadata row helper */
function MetaRow({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-start gap-2 text-xs">
      <span className="text-slate-500 mt-0.5 shrink-0">{icon}</span>
      <span className="text-slate-500 shrink-0 w-16">{label}</span>
      <span className="text-slate-300 break-all">{value}</span>
    </div>
  );
}
