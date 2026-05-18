/**
 * Firebase initialization module.
 *
 * Mode is controlled by the VITE_AUTH_MODE environment variable:
 *   - "production" (default) → connects to the real Firebase cloud backend
 *   - "emulator"             → connects to local Firebase emulators
 *
 * Set VITE_AUTH_MODE=emulator in .env.docker to enable local emulators.
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

const authMode = (import.meta.env.VITE_AUTH_MODE ?? 'production').toLowerCase();
const useEmulators = authMode === 'emulator';

const projectId = import.meta.env.VITE_FIREBASE_PROJECT_ID || 'roadguard-demo';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || `${projectId}.firebaseapp.com`,
  projectId,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || `${projectId}.appspot.com`,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
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

if (useEmulators && !globalThis.__ROADGUARD_EMULATORS_CONNECTED__) {
  const authHost = import.meta.env.VITE_AUTH_EMULATOR_HOST || '127.0.0.1';
  const authPort = Number(import.meta.env.VITE_AUTH_EMULATOR_PORT || '9099');
  const firestoreHost = import.meta.env.VITE_FIRESTORE_EMULATOR_HOST || '127.0.0.1';
  const firestorePort = Number(import.meta.env.VITE_FIRESTORE_EMULATOR_PORT || '8080');

  connectAuthEmulator(auth, `http://${authHost}:${authPort}`, { disableWarnings: true });
  connectFirestoreEmulator(db, firestoreHost, firestorePort);
  globalThis.__ROADGUARD_EMULATORS_CONNECTED__ = true;

  console.info('[Firebase] Running against local emulators.');
} else if (!useEmulators) {
  console.info('[Firebase] Running against production cloud backend.');
}

export const isFirebaseConfigured = true;

export { app, auth, db };
export default app;
