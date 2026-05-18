/**
 * Firebase Auth hook.
 *
 * Wraps onAuthStateChanged and extracts custom claims (role).
 * Returns user state, role information, and auth actions.
 */

import { useState, useEffect, useCallback } from 'react';
import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  type User,
} from 'firebase/auth';
import { auth } from '../config/firebase';
import type { UserRole } from '../types/report';

const emulatorAuthMode = (import.meta.env.VITE_AUTH_MODE ?? 'emulator').toLowerCase() === 'emulator';
const emulatorOperatorEmailsRaw: string =
  import.meta.env.VITE_EMULATOR_OPERATOR_EMAILS || 'operator@roadguard.it';
const emulatorOperatorEmails = new Set(
  emulatorOperatorEmailsRaw
    .split(',')
    .map((email: string) => email.trim().toLowerCase())
    .filter((email: string) => email.length > 0),
);

function resolveRole(firebaseUser: User, claimRole: unknown): UserRole {
  if (claimRole === 'admin' || claimRole === 'operator' || claimRole === 'user') {
    return claimRole;
  }

  const normalizedEmail = (firebaseUser.email || '').toLowerCase();

  // For thesis demo robustness, treat specific cloud emails as admins/operators in the frontend
  if (normalizedEmail.includes('admin') || normalizedEmail.endsWith('@roadguard.local') || normalizedEmail.includes('operator')) {
    return 'admin';
  }

  if (emulatorAuthMode) {
    if (normalizedEmail && emulatorOperatorEmails.has(normalizedEmail)) {
      return 'operator';
    }
  }

  return 'user';
}

interface AuthState {
  user: User | null;
  role: UserRole;
  isOperator: boolean;
  isAdmin: boolean;
  isLoading: boolean;
  error: string | null;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

export function useAuth(): AuthState {
  const [user, setUser] = useState<User | null>(null);
  const [role, setRole] = useState<UserRole>('user');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      if (firebaseUser) {
        setUser(firebaseUser);
        // Extract role from custom claims
        try {
          const tokenResult = await firebaseUser.getIdTokenResult();
          setRole(resolveRole(firebaseUser, tokenResult.claims.role));
        } catch {
          setRole('user');
        }
      } else {
        setUser(null);
        setRole('user');
      }
      setIsLoading(false);
    });

    return unsubscribe;
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    setError(null);
    setIsLoading(true);
    try {
      const credential = await signInWithEmailAndPassword(auth, email, password);
      const tokenResult = await credential.user.getIdTokenResult();
      setRole(resolveRole(credential.user, tokenResult.claims.role));
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Authentication failed';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const signOut = useCallback(async () => {
    await firebaseSignOut(auth);
    setUser(null);
    setRole('user');
  }, []);

  return {
    user,
    role,
    isOperator: role === 'operator' || role === 'admin',
    isAdmin: role === 'admin',
    isLoading,
    error,
    signIn,
    signOut,
  };
}
