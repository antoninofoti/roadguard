/**
 * Firebase initialization module for the final rollout.
 *
 * Strategy is intentionally single-mode: Auth Emulator.
 */

import { initializeApp, type FirebaseApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, type Auth } from 'firebase/auth';
import {
  connectFirestoreEmulator,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from 'firebase/firestore';

const authMode = (import.meta.env.VITE_AUTH_MODE ?? 'emulator').toLowerCase();
if (authMode !== 'emulator') {
  throw new Error(
    '[Firebase] Unsupported auth mode. Set VITE_AUTH_MODE=emulator for this rollout.',
  );
}

const projectId = import.meta.env.VITE_FIREBASE_PROJECT_ID || 'roadguard-demo';
const authHost = import.meta.env.VITE_AUTH_EMULATOR_HOST || '127.0.0.1';
const authPort = Number(import.meta.env.VITE_AUTH_EMULATOR_PORT || '9099');
const firestoreHost = import.meta.env.VITE_FIRESTORE_EMULATOR_HOST || '127.0.0.1';
const firestorePort = Number(import.meta.env.VITE_FIRESTORE_EMULATOR_PORT || '8080');

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || 'demo-api-key',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || `${projectId}.firebaseapp.com`,
  projectId,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || `${projectId}.appspot.com`,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '000000000000',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '1:000000000000:web:0000000000000000',
};

declare global {
  var __ROADGUARD_EMULATORS_CONNECTED__: boolean | undefined;
}

const app: FirebaseApp = initializeApp(firebaseConfig);
const auth: Auth = getAuth(app);
const db: Firestore = initializeFirestore(app, {
  localCache: persistentLocalCache({
    tabManager: persistentMultipleTabManager(),
  }),
});

if (!globalThis.__ROADGUARD_EMULATORS_CONNECTED__) {
  connectAuthEmulator(auth, `http://${authHost}:${authPort}`, {
    disableWarnings: true,
  });
  connectFirestoreEmulator(db, firestoreHost, firestorePort);
  globalThis.__ROADGUARD_EMULATORS_CONNECTED__ = true;
}

export const isFirebaseConfigured = true;

export { app, auth, db };
export default app;
