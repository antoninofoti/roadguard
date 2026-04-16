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
          const claimRole = (tokenResult.claims.role as UserRole) || 'user';
          setRole(claimRole);
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
      const claimRole = (tokenResult.claims.role as UserRole) || 'user';
      setRole(claimRole);
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
