/**
 * Auth Context Provider.
 *
 * Wraps the useAuth hook and provides auth state to the component tree.
 */

import type { ReactNode } from 'react';
import { useAuth } from '../hooks/useAuth';
import { AuthContext } from './authContext';

export function AuthProvider({ children }: { children: ReactNode }) {
  const authState = useAuth();

  return (
    <AuthContext.Provider value={authState}>
      {children}
    </AuthContext.Provider>
  );
}
