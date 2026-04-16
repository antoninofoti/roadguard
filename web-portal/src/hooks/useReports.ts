/**
 * Real-time Firestore listener for reports.
 *
 * Uses onSnapshot for real-time updates so operators see
 * new reports appear instantly without polling.
 *
 * Mirrors the query logic from ReportRepository.kt.
 */

import { useState, useEffect, useMemo } from 'react';
import {
  collection,
  query,
  where,
  orderBy,
  onSnapshot,
  type QueryConstraint,
} from 'firebase/firestore';
import { db } from '../config/firebase';
import type { Report, ReportFilters } from '../types/report';

interface UseReportsResult {
  reports: Report[];
  isLoading: boolean;
  error: string | null;
}

export function useReports(filters: ReportFilters): UseReportsResult {
  const [reports, setReports] = useState<Report[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Build the Firestore query constraints from filters
  // Status is queried server-side; severity & damageType are filtered client-side
  const statusFilter = useMemo(
    () => filters.statuses.length > 0 ? filters.statuses : ['PENDING'],
    [filters.statuses]
  );

  useEffect(() => {
    setIsLoading(true);
    setError(null);

    const constraints: QueryConstraint[] = [];

    // Firestore 'in' supports up to 30 values
    if (statusFilter.length > 0 && statusFilter.length <= 30) {
      constraints.push(where('status', 'in', statusFilter));
    }

    constraints.push(orderBy('timestamp', 'desc'));

    const q = query(collection(db, 'reports'), ...constraints);

    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        const docs = snapshot.docs.map((doc) => {
          const data = doc.data();
          return {
            id: doc.id,
            imageUrl: data.imageUrl ?? '',
            location: data.location ?? null,
            timestamp: data.timestamp ?? null,
            userId: data.userId ?? '',
            severity: data.severity ?? 0,
            status: data.status ?? 'PENDING',
            detectionSource: data.detectionSource ?? 'MANUAL',
            cvConfidence: data.cvConfidence ?? 0,
            sensorConfidence: data.sensorConfidence ?? 0,
            fusedScore: data.fusedScore ?? 0,
            damageType: data.damageType ?? '',
            operatorId: data.operatorId ?? '',
            resolvedAt: data.resolvedAt ?? null,
            notes: data.notes ?? '',
          } as Report;
        });

        // Client-side filtering for severity range and damage type
        const filtered = docs.filter((report) => {
          const severityOk =
            report.severity >= filters.severityMin &&
            report.severity <= filters.severityMax;

          const damageTypeOk =
            filters.damageTypes.length === 0 ||
            filters.damageTypes.includes(report.damageType);

          return severityOk && damageTypeOk;
        });

        setReports(filtered);
        setIsLoading(false);
      },
      (err) => {
        console.error('[useReports] Firestore error:', err);
        setError(err.message);
        setIsLoading(false);
      }
    );

    return () => unsubscribe();
  }, [statusFilter, filters.severityMin, filters.severityMax, filters.damageTypes]);

  return { reports, isLoading, error };
}
