import assert from 'node:assert/strict';
import { initializeApp } from 'firebase/app';
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth,
  signInWithEmailAndPassword,
  signOut,
} from 'firebase/auth';

const projectId = process.env.GCLOUD_PROJECT || 'roadguard-demo';
const apiKey = 'demo-api-key';
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const operatorAllowlist = new Set(['operator.release@roadguard.local']);

function firebaseConfig(id) {
  return {
    apiKey,
    authDomain: `${id}.firebaseapp.com`,
    projectId: id,
    appId: `1:000000000000:web:${id}`,
  };
}

async function upsertUser(auth, email, password) {
  try {
    return (await createUserWithEmailAndPassword(auth, email, password)).user;
  } catch (error) {
    if (error?.code === 'auth/email-already-in-use') {
      return (await signInWithEmailAndPassword(auth, email, password)).user;
    }
    throw error;
  }
}

async function main() {
  const app = initializeApp(firebaseConfig(projectId));
  const auth = getAuth(app);

  connectAuthEmulator(auth, `http://${authHost}`, { disableWarnings: true });

  const operatorEmail = 'operator.release@roadguard.local';
  const operatorPassword = 'RoadGuard!2026';
  let passed = 0;

  console.log('Auth Emulator Login Checks');

  // Ensure a deterministic operator account exists.
  await upsertUser(auth, operatorEmail, operatorPassword);
  await signOut(auth);

  const login = await signInWithEmailAndPassword(auth, operatorEmail, operatorPassword);
  const idToken = await login.user.getIdToken();
  assert.ok(idToken && idToken.length > 0, 'ID token must be present');
  passed += 1;
  console.log('- PASS: valid login returns ID token');

  const identityEmail = (login.user.email || '').toLowerCase();
  assert.ok(operatorAllowlist.has(identityEmail));
  passed += 1;
  console.log('- PASS: protected access identity is in operator allowlist');

  await signOut(auth);

  let invalidRejected = false;
  try {
    await signInWithEmailAndPassword(auth, operatorEmail, 'wrong-password');
  } catch {
    invalidRejected = true;
  }

  assert.ok(invalidRejected, 'Invalid credentials must be rejected');
  passed += 1;
  console.log('- PASS: invalid login is rejected');

  console.log(`Summary: ${passed}/3 checks passed`);
}

main().catch((error) => {
  console.error('Auth Emulator Login Checks FAILED');
  console.error(error);
  process.exit(1);
});
