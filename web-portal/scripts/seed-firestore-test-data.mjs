#!/usr/bin/env node

/**
 * Seed Firestore emulator with realistic test road damage reports.
 *
 * Creates 15 diverse road damage reports in Rome region for dashboard testing.
 * Reports span multiple severity levels, damage types, and statuses.
 *
 * Usage:
 *   node scripts/seed-firestore-test-data.mjs
 */

import { initializeApp } from "firebase/app";
import {
  connectFirestoreEmulator,
  initializeFirestore,
  collection,
  addDoc,
} from "firebase/firestore";

// Firebase config (same as portal's emulator config)
const firebaseConfig = {
  apiKey: "demo-api-key",
  authDomain: "roadguard-demo.firebaseapp.com",
  projectId: "roadguard-demo",
  storageBucket: "roadguard-demo.appspot.com",
  messagingSenderId: "000000000000",
  appId: "1:000000000000:web:0000000000000000",
};

// Initialize Firebase and Firestore (emulator mode)
const app = initializeApp(firebaseConfig);
const db = initializeFirestore(app, {});
connectFirestoreEmulator(db, "127.0.0.1", 8080);

// Test data generator
const damageTypes = ["pothole", "bump", "speed_bump", "roughness"];
const statuses = ["PENDING", "CONFIRMED", "REJECTED", "RESOLVED"];
const locations = [
  { lat: 41.9028, lng: 12.4964, name: "Colosseum area" },
  { lat: 41.9032, lng: 12.4969, name: "Roman Forum" },
  { lat: 41.9015, lng: 12.4955, name: "Palatine Hill" },
  { lat: 41.8954, lng: 12.4912, name: "Trastevere" },
  { lat: 41.9107, lng: 12.4607, name: "Borghese Gardens" },
  { lat: 41.9206, lng: 12.4789, name: "Spanish Steps" },
  { lat: 41.8909, lng: 12.4901, name: "Aventine Hill" },
  { lat: 41.877, lng: 12.4895, name: "San Paolo" },
];

function generateTestReports(count = 15) {
  const reports = [];
  const now = new Date();

  for (let i = 0; i < count; i++) {
    const loc = locations[i % locations.length];
    const damageType =
      damageTypes[Math.floor(Math.random() * damageTypes.length)];
    const status = statuses[Math.floor(Math.random() * statuses.length)];

    // Simulate sensor fusion scores
    const cvConfidence = 0.6 + Math.random() * 0.35;
    const sensorConfidence = 0.5 + Math.random() * 0.45;
    const fusedScore = cvConfidence * 0.6 + sensorConfidence * 0.4; // Weighted fusion
    const severity = fusedScore * 0.8 + Math.random() * 0.2; // Severity correlated with fusion

    reports.push({
      id: `report-${Date.now()}-${i}`,
      damageType,
      severity: Math.min(severity, 1.0),
      fusedScore: Math.min(fusedScore, 1.0),
      cvConfidence,
      sensorConfidence,
      status,
      location: {
        latitude: loc.lat + (Math.random() - 0.5) * 0.01,
        longitude: loc.lng + (Math.random() - 0.5) * 0.01,
      },
      timestamp: new Date(
        now.getTime() - Math.random() * 7 * 24 * 60 * 60 * 1000,
      ),
      imageUrl: null, // No real images in test
      operatorNotes: [
        "",
        `Needs inspection`,
        `Marked for repair`,
        `Already repaired`,
      ][Math.floor(Math.random() * 4)],
      createdBy: "operator.release@roadguard.local",
      lastModified: new Date(),
      lastModifiedBy: "operator.release@roadguard.local",
    });
  }

  return reports;
}

async function seedFirestore() {
  console.log("🌱 Seeding Firestore emulator with test reports...\n");

  try {
    const reports = generateTestReports(15);
    const reportsRef = collection(db, "reports");

    let successCount = 0;
    for (const report of reports) {
      await addDoc(reportsRef, report);
      successCount++;
      console.log(
        `  ✓ Added: ${report.damageType} (${report.severity.toFixed(2)} severity) @ ${report.location.latitude.toFixed(4)}, ${report.location.longitude.toFixed(4)}`,
      );
    }

    console.log(`\n✅ Successfully seeded ${successCount} test reports!\n`);
    console.log("📍 Reports by type:");
    const byType = {};
    reports.forEach((r) => {
      byType[r.damageType] = (byType[r.damageType] || 0) + 1;
    });
    Object.entries(byType).forEach(([type, count]) => {
      console.log(`   - ${type}: ${count}`);
    });

    console.log("\n📊 Reports by status:");
    const byStatus = {};
    reports.forEach((r) => {
      byStatus[r.status] = (byStatus[r.status] || 0) + 1;
    });
    Object.entries(byStatus).forEach(([status, count]) => {
      console.log(`   - ${status}: ${count}`);
    });

    console.log(
      "\n🎯 Open the portal at http://127.0.0.1:4501/ to see the reports on the map!\n",
    );

    process.exit(0);
  } catch (error) {
    console.error("❌ Error seeding Firestore:", error);
    process.exit(1);
  }
}

// Run seeding
seedFirestore();
