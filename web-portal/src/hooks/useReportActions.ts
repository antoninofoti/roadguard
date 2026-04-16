/**
 * Report action mutations.
 *
 * Mirrors ReportRepository.updateReportStatus() from the Android app.
 * Provides confirm/reject/resolve functions for operator workflow.
 */

import { useCallback, useState } from 'react';
import { doc, updateDoc, serverTimestamp } from 'firebase/firestore';
import { db, auth } from '../config/firebase';
import type { ReportStatus } from '../types/report';

interface UseReportActionsResult {
  updateStatus: (
    reportId: string,
    newStatus: ReportStatus,
    notes?: string
  ) => Promise<void>;
  confirmReport: (reportId: string, notes?: string) => Promise<void>;
  rejectReport: (reportId: string, notes?: string) => Promise<void>;
  resolveReport: (reportId: string, notes?: string) => Promise<void>;
  isUpdating: boolean;
  error: string | null;
}

export function useReportActions(): UseReportActionsResult {
  const [isUpdating, setIsUpdating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const updateStatus = useCallback(
    async (reportId: string, newStatus: ReportStatus, notes?: string) => {
      const operatorId = auth.currentUser?.uid;
      if (!operatorId) {
        setError('Not authenticated');
        return;
      }

      setIsUpdating(true);
      setError(null);

      try {
        const reportRef = doc(db, 'reports', reportId);
        const updates: Record<string, unknown> = {
          status: newStatus,
          operatorId,
          notes: notes ?? '',
        };

        if (newStatus === 'RESOLVED') {
          updates.resolvedAt = serverTimestamp();
          updates.resolvedBy = operatorId;
        }

        await updateDoc(reportRef, updates);
      } catch (err: unknown) {
        const message =
          err instanceof Error ? err.message : 'Failed to update report';
        setError(message);
        throw err;
      } finally {
        setIsUpdating(false);
      }
    },
    []
  );

  const confirmReport = useCallback(
    (reportId: string, notes?: string) =>
      updateStatus(reportId, 'CONFIRMED', notes),
    [updateStatus]
  );

  const rejectReport = useCallback(
    (reportId: string, notes?: string) =>
      updateStatus(reportId, 'REJECTED', notes),
    [updateStatus]
  );

  const resolveReport = useCallback(
    (reportId: string, notes?: string) =>
      updateStatus(reportId, 'RESOLVED', notes),
    [updateStatus]
  );

  return {
    updateStatus,
    confirmReport,
    rejectReport,
    resolveReport,
    isUpdating,
    error,
  };
}
