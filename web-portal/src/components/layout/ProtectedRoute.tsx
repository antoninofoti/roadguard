/**
 * Protected route wrapper.
 * Redirects to login if user is not authenticated or not an operator.
 */

import { Navigate } from 'react-router-dom';
import { useAuthContext } from '../../context/AuthContext';
import { Shield } from 'lucide-react';

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, isOperator, isLoading } = useAuthContext();

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[var(--color-surface-900)]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 border-3 border-[var(--color-accent-primary)] border-t-transparent rounded-full animate-spin" />
          <p className="text-slate-400 text-sm">Authenticating...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!isOperator) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-[var(--color-surface-900)]">
        <div className="glass-card p-8 max-w-md text-center">
          <Shield className="w-16 h-16 text-[var(--color-accent-danger)] mx-auto mb-4" />
          <h2 className="text-xl font-semibold text-white mb-2">Access Denied</h2>
          <p className="text-slate-400 mb-4">
            This portal is restricted to operators and administrators.
            Contact your system administrator for access.
          </p>
          <button
            onClick={() => window.location.href = '/login'}
            className="px-4 py-2 bg-[var(--color-surface-700)] hover:bg-[var(--color-surface-600)] rounded-lg text-sm text-slate-300 transition-colors"
          >
            Back to Login
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
