import { readFile } from 'node:fs/promises';
import process from 'node:process';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

const projectId = 'roadguard-rbac-test';
const rules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8');

const testEnv = await initializeTestEnvironment({
  projectId,
  firestore: {
    host: '127.0.0.1',
    port: 8080,
    rules,
  },
});

const citizenUid = 'citizen-uid';
const operatorUid = 'operator-uid';
const adminUid = 'admin-uid';
const otherCitizenUid = 'other-citizen-uid';

const reportOwnPath = 'reports/report-owned-by-citizen';
const reportOtherPath = 'reports/report-owned-by-other';

await testEnv.withSecurityRulesDisabled(async (context) => {
  const db = context.firestore();

  await setDoc(doc(db, reportOwnPath), {
    userId: citizenUid,
    status: 'PENDING',
    damageType: 'pothole',
    fusedScore: 0.72,
    severity: 0.64,
    imageUrl: 'https://example.com/r1.jpg',
  });

  await setDoc(doc(db, reportOtherPath), {
    userId: otherCitizenUid,
    status: 'PENDING',
    damageType: 'bump',
    fusedScore: 0.55,
    severity: 0.51,
    imageUrl: 'https://example.com/r2.jpg',
  });
});

const citizenDb = testEnv.authenticatedContext(citizenUid, { role: 'citizen' }).firestore();
const operatorDb = testEnv.authenticatedContext(operatorUid, { role: 'operator' }).firestore();
const adminDb = testEnv.authenticatedContext(adminUid, { role: 'admin' }).firestore();

const checks = [];

async function expectAllowed(label, action) {
  try {
    await assertSucceeds(action());
    checks.push({ label, passed: true });
  } catch (error) {
    checks.push({ label, passed: false, error: String(error) });
  }
}

async function expectDenied(label, action) {
  try {
    await assertFails(action());
    checks.push({ label, passed: true });
  } catch (error) {
    checks.push({ label, passed: false, error: String(error) });
  }
}

await expectAllowed('citizen can read own report', () =>
  getDoc(doc(citizenDb, reportOwnPath))
);

await expectDenied('citizen cannot read another citizen report', () =>
  getDoc(doc(citizenDb, reportOtherPath))
);

await expectDenied('citizen cannot set operator status fields', () =>
  updateDoc(doc(citizenDb, reportOwnPath), {
    status: 'CONFIRMED',
  })
);

await expectAllowed('operator can confirm report with operator fields', () =>
  updateDoc(doc(operatorDb, reportOwnPath), {
    status: 'CONFIRMED',
    operatorId: operatorUid,
    notes: 'validated in rehearsal',
  })
);

await expectDenied('operator cannot change severity directly', () =>
  updateDoc(doc(operatorDb, reportOwnPath), {
    severity: 0.95,
  })
);

await expectAllowed('admin can change severity', () =>
  updateDoc(doc(adminDb, reportOwnPath), {
    severity: 0.95,
  })
);

await testEnv.cleanup();

console.log('RBAC Firestore Rules Checks');
for (const check of checks) {
  const status = check.passed ? 'PASS' : 'FAIL';
  console.log(`- ${status}: ${check.label}`);
  if (!check.passed) {
    console.log(`  error: ${check.error}`);
  }
}

const failures = checks.filter((c) => !c.passed).length;
console.log(`Summary: ${checks.length - failures}/${checks.length} checks passed`);

if (failures > 0) {
  process.exit(1);
}
