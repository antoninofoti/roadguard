import { createContext, useContext } from 'react';
import type { User } from 'firebase/auth';
import type { UserRole } from '../types/report';

export interface AuthContextType {
  user: User | null;
  role: UserRole;
  isOperator: boolean;
  isAdmin: boolean;
  isLoading: boolean;
  error: string | null;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function useAuthContext(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuthContext must be used within an AuthProvider');
  }
  return context;
}