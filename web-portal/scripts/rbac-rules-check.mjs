import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeApp } from 'firebase/app';
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signInWithCustomToken,
  signInWithEmailAndPassword,
  signOut,
} from 'firebase/auth';
import {
  connectFirestoreEmulator,
  doc,
  getDoc,
  getFirestore,
  setDoc,
  updateDoc,
} from 'firebase/firestore';

const projectId = process.env.GCLOUD_PROJECT || 'roadguard-demo';
const apiKey = 'demo-api-key';
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const firestoreRulesEndpoint = `http://127.0.0.1:8080/emulator/v1/projects/${projectId}:securityRules`;

function firebaseConfig(id) {
  return {
    apiKey,
    authDomain: `${id}.firebaseapp.com`,
    projectId: id,
    appId: `1:000000000000:web:${id}`,
  };
}

function isPermissionDenied(error) {
  const code = `${error?.code ?? ''}`.toLowerCase();
  const message = `${error?.message ?? ''}`.toLowerCase();
  return code.includes('permission-denied') || message.includes('permission_denied');
}

async function loadFirestoreRules() {
  const rulesFile = new URL('../../firestore.rules', import.meta.url);
  const rules = readFileSync(rulesFile, 'utf8');

  const response = await fetch(firestoreRulesEndpoint, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      rules: {
        files: [
          {
            name: 'firestore.rules',
            content: rules,
          },
        ],
      },
    }),
  });

  if (!response.ok) {
    const details = await response.text();
    throw new Error(`Failed to load Firestore rules into emulator: ${response.status} ${details}`);
  }
}

async function expectAllowed(label, action) {
  await action();
  console.log(`- PASS: ${label}`);
}

async function expectDenied(label, action) {
  try {
    await action();
  } catch (error) {
    if (isPermissionDenied(error)) {
      console.log(`- PASS: ${label}`);
      return;
    }
    throw error;
  }
  throw new Error(`Expected permission denied for check: ${label}`);
}

function createUnsignedCustomToken(uid, claims = {}) {
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: 'firebase-auth-emulator@example.com',
    sub: 'firebase-auth-emulator@example.com',
    aud: 'https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit',
    iat: now,
    exp: now + 3600,
    uid,
    claims,
  };

  const toBase64Url = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
  return `${toBase64Url({ alg: 'none', typ: 'JWT' })}.${toBase64Url(payload)}.`;
}

async function upsertUser(auth, email, password) {
  let user;
  try {
    user = (await createUserWithEmailAndPassword(auth, email, password)).user;
  } catch (error) {
    if (error?.code !== 'auth/email-already-in-use') {
      throw error;
    }
    user = (await signInWithEmailAndPassword(auth, email, password)).user;
  }

  await signOut(auth);
  return user;
}

async function signIn(auth, email, password) {
  await signInWithEmailAndPassword(auth, email, password);
}

async function main() {
  const app = initializeApp(firebaseConfig(projectId));
  const auth = getAuth(app);
  const db = getFirestore(app);

  connectAuthEmulator(auth, `http://${authHost}`, { disableWarnings: true });
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
  await loadFirestoreRules();

  console.log('RBAC Firestore Rules Checks');

  const users = {
    citizenA: { email: 'citizen.a@roadguard.local', password: 'RoadGuard!2026' },
    citizenB: { email: 'citizen.b@roadguard.local', password: 'RoadGuard!2026' },
    operator: { uid: 'operator-rbac-role' },
    admin: { uid: 'admin-rbac-role' },
  };

  const citizenA = await upsertUser(auth, users.citizenA.email, users.citizenA.password);
  const citizenB = await upsertUser(auth, users.citizenB.email, users.citizenB.password);

  const runId = `${Date.now()}`;
  const reportOwnRef = doc(db, 'reports', `rbac-own-${runId}`);
  const reportOtherRef = doc(db, 'reports', `rbac-other-${runId}`);

  await signIn(auth, users.citizenA.email, users.citizenA.password);
  await setDoc(reportOwnRef, {
    userId: citizenA.uid,
    status: 'PENDING',
    damageType: 'pothole',
    severity: 0.4,
    fusedScore: 0.6,
    description: 'seed own report',
  });
  await signOut(auth);

  await signIn(auth, users.citizenB.email, users.citizenB.password);
  await setDoc(reportOtherRef, {
    userId: citizenB.uid,
    status: 'PENDING',
    damageType: 'bump',
    severity: 0.3,
    fusedScore: 0.5,
    description: 'seed other report',
  });
  await signOut(auth);

  await signIn(auth, users.citizenA.email, users.citizenA.password);

  await expectAllowed('citizen can read own report', async () => {
    const snapshot = await getDoc(reportOwnRef);
    assert.ok(snapshot.exists(), 'Own report should exist');
  });

  await expectDenied('citizen cannot read another citizen report', async () => {
    await getDoc(reportOtherRef);
  });

  await expectDenied('citizen cannot set operator status fields', async () => {
    await updateDoc(reportOwnRef, {
      status: 'CONFIRMED',
      operatorNotes: 'attempted by citizen',
    });
  });

  await signOut(auth);

  await signInWithCustomToken(
    auth,
    createUnsignedCustomToken(users.operator.uid, { role: 'operator' }),
  );

  await expectAllowed('operator can confirm report with operator fields', async () => {
    await updateDoc(reportOwnRef, {
      status: 'CONFIRMED',
      operatorNotes: 'validated by operator',
    });
  });

  await expectDenied('operator cannot change severity directly', async () => {
    await updateDoc(reportOwnRef, {
      severity: 0.9,
    });
  });

  await signOut(auth);

  await signInWithCustomToken(
    auth,
    createUnsignedCustomToken(users.admin.uid, { role: 'admin' }),
  );

  await expectAllowed('admin can change severity', async () => {
    await updateDoc(reportOwnRef, {
      severity: 0.9,
    });
  });

  await signOut(auth);

  console.log('Summary: 6/6 checks passed');
}

main().catch((error) => {
  console.error('RBAC Firestore Rules Checks FAILED');
  console.error(error);
  process.exit(1);
});
