/**
 * Real-time Firestore listener for reports.
 *
 * Uses onSnapshot for real-time updates so operators see
 * new reports appear instantly without polling.
 *
 * Mirrors the query logic from ReportRepository.kt.
 */

import { useState, useEffect, useMemo } from "react";
import {
  collection,
  GeoPoint,
  Timestamp,
  query,
  where,
  orderBy,
  onSnapshot,
  type QueryConstraint,
} from "firebase/firestore";
import { db } from "../config/firebase";
import type { Report, ReportFilters } from "../types/report";

interface UseReportsResult {
  reports: Report[];
  isLoading: boolean;
  error: string | null;
}

function asNumber(value: unknown, fallback = 0): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function normalizeTimestamp(value: unknown): Timestamp | null {
  if (value instanceof Timestamp) return value;
  if (value instanceof Date) return Timestamp.fromDate(value);
  if (typeof value === "number") return Timestamp.fromMillis(value);
  if (typeof value === "string") {
    const millis = Date.parse(value);
    if (!Number.isNaN(millis)) return Timestamp.fromMillis(millis);
  }
  if (value && typeof value === "object") {
    const maybe = value as { seconds?: unknown; nanoseconds?: unknown };
    if (typeof maybe.seconds === "number") {
      return new Timestamp(
        maybe.seconds,
        typeof maybe.nanoseconds === "number" ? maybe.nanoseconds : 0,
      );
    }
  }
  return null;
}

function normalizeGeoPoint(value: unknown): GeoPoint | null {
  if (value instanceof GeoPoint) return value;
  if (value && typeof value === "object") {
    const maybe = value as {
      latitude?: unknown;
      longitude?: unknown;
      lat?: unknown;
      lng?: unknown;
    };
    const latitude =
      typeof maybe.latitude === "number"
        ? maybe.latitude
        : typeof maybe.lat === "number"
          ? maybe.lat
          : null;
    const longitude =
      typeof maybe.longitude === "number"
        ? maybe.longitude
        : typeof maybe.lng === "number"
          ? maybe.lng
          : null;
    if (latitude !== null && longitude !== null) {
      return new GeoPoint(latitude, longitude);
    }
  }
  return null;
}

function normalizeDetectionSource(value: unknown): Report["detectionSource"] {
  const source = asString(value, "MANUAL").toUpperCase();
  if (
    source === "CV_ONLY" ||
    source === "SENSOR_ONLY" ||
    source === "DUAL_CONFIRMED" ||
    source === "MANUAL"
  ) {
    return source;
  }
  return "MANUAL";
}

function normalizeStatus(value: unknown): Report["status"] {
  const status = asString(value, "PENDING").toUpperCase();
  if (
    status === "PENDING" ||
    status === "CONFIRMED" ||
    status === "REJECTED" ||
    status === "RESOLVED"
  ) {
    return status;
  }
  return "PENDING";
}

export function useReports(filters: ReportFilters): UseReportsResult {
  const [reports, setReports] = useState<Report[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Build the Firestore query constraints from filters
  // Status is queried server-side; severity & damageType are filtered client-side
  const statusFilter = useMemo(
    () => (filters.statuses.length > 0 ? filters.statuses : ["PENDING"]),
    [filters.statuses],
  );

  useEffect(() => {
    const constraints: QueryConstraint[] = [];

    // Firestore 'in' supports up to 30 values
    if (statusFilter.length > 0 && statusFilter.length <= 30) {
      constraints.push(where("status", "in", statusFilter));
    }

    constraints.push(orderBy("timestamp", "desc"));

    const q = query(collection(db, "reports"), ...constraints);

    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        const docs = snapshot.docs.map((doc) => {
          const data = doc.data();
          const fusedScore = asNumber(
            data.fusedScore,
            asNumber(data.severity, 0),
          );
          const severity = asNumber(data.severity, fusedScore);
          return {
            id: doc.id,
            imageUrl: asString(
              data.imageUrl,
              asString(data.imageUri, asString(data.photoUrl, "")),
            ),
            location: normalizeGeoPoint(data.location),
            timestamp: normalizeTimestamp(data.timestamp),
            userId: asString(
              data.userId,
              asString(data.uid, asString(data.reporterId, "")),
            ),
            severity,
            status: normalizeStatus(data.status),
            detectionSource: normalizeDetectionSource(data.detectionSource),
            cvConfidence: asNumber(data.cvConfidence),
            sensorConfidence: asNumber(data.sensorConfidence),
            fusedScore,
            damageType: asString(
              data.damageType,
              asString(data.type, "unknown"),
            ),
            operatorId: asString(data.operatorId),
            resolvedAt: normalizeTimestamp(data.resolvedAt),
            notes: asString(data.notes),
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

        setError(null);
        setReports(filtered);
        setIsLoading(false);
      },
      (err) => {
        console.error("[useReports] Firestore error:", err);
        setError(err.message);
        setIsLoading(false);
      },
    );

    return () => unsubscribe();
  }, [
    statusFilter,
    filters.severityMin,
    filters.severityMax,
    filters.damageTypes,
  ]);

  return { reports, isLoading, error };
}
