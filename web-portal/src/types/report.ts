/**
 * TypeScript interfaces mirroring the Kotlin data models.
 *
 * Mirrors:
 * - com.example.roadguard.model.Report
 * - com.example.roadguard.model.ReportStatus
 * - com.example.roadguard.model.DetectionSource
 * - com.example.roadguard.model.User
 */

import type { GeoPoint, Timestamp } from "firebase/firestore";

// ── Report Status (mirrors ReportStatus.kt) ───────────────────

export const REPORT_STATUSES = [
  "PENDING",
  "CONFIRMED",
  "REJECTED",
  "RESOLVED",
] as const;
export type ReportStatus = (typeof REPORT_STATUSES)[number];

// ── Detection Source (mirrors DetectionSource.kt) ─────────────

export const DETECTION_SOURCES = [
  "CV_ONLY",
  "SENSOR_ONLY",
  "DUAL_CONFIRMED",
  "MANUAL",
] as const;
export type DetectionSource = (typeof DETECTION_SOURCES)[number];

// ── Damage Types ──────────────────────────────────────────────

export const DAMAGE_TYPES = [
  "pothole",
  "bump",
  "speed_bump",
  "roughness",
] as const;
export type DamageType = (typeof DAMAGE_TYPES)[number];

// ── Report Interface (mirrors Report.kt) ──────────────────────

export interface Report {
  id: string;
  imageUrl: string;
  location: GeoPoint | null;
  timestamp: Timestamp | null;
  userId: string;
  severity: number; // 0.0 - 1.0

  // Fusion metadata
  status: ReportStatus;
  detectionSource: DetectionSource;
  cvConfidence: number;
  sensorConfidence: number;
  fusedScore: number;
  damageType: string;

  // Operator workflow
  operatorId: string;
  resolvedAt: Timestamp | null;
  notes: string;
}

// ── User Interface (mirrors User.kt) ─────────────────────────

export const USER_ROLES = {
  CITIZEN: "user",
  OPERATOR: "operator",
  ADMIN: "admin",
} as const;

export type UserRole = (typeof USER_ROLES)[keyof typeof USER_ROLES];

export interface AppUser {
  uid: string;
  email: string;
  role: UserRole;
}

// ── Filter State ──────────────────────────────────────────────

export interface ReportFilters {
  statuses: ReportStatus[];
  severityMin: number;
  severityMax: number;
  damageTypes: string[];
}

export const DEFAULT_FILTERS: ReportFilters = {
  statuses: ["PENDING", "CONFIRMED", "REJECTED", "RESOLVED"],
  severityMin: 0,
  severityMax: 1,
  damageTypes: [],
};

// ── Severity Helpers ──────────────────────────────────────────

export type SeverityLevel = "low" | "medium" | "high" | "critical";

export function getSeverityLevel(severity: number): SeverityLevel {
  if (severity < 0.25) return "low";
  if (severity < 0.5) return "medium";
  if (severity < 0.75) return "high";
  return "critical";
}

export function getSeverityColor(severity: number): string {
  const level = getSeverityLevel(severity);
  const colors: Record<SeverityLevel, string> = {
    low: "#22c55e",
    medium: "#eab308",
    high: "#f97316",
    critical: "#ef4444",
  };
  return colors[level];
}

export function getStatusColor(status: ReportStatus): string {
  const colors: Record<ReportStatus, string> = {
    PENDING: "#f59e0b",
    CONFIRMED: "#3b82f6",
    REJECTED: "#ef4444",
    RESOLVED: "#22c55e",
  };
  return colors[status];
}

export function formatDamageType(type: string): string {
  return type.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export function formatDetectionSource(source: DetectionSource): string {
  const labels: Record<DetectionSource, string> = {
    CV_ONLY: "Computer Vision",
    SENSOR_ONLY: "IMU Sensors",
    DUAL_CONFIRMED: "Dual Confirmed",
    MANUAL: "Manual Report",
  };
  return labels[source];
}
