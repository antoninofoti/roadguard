/**
 * Top header bar with user info and sign out.
 */

import { LogOut, Shield, ShieldCheck } from 'lucide-react';
import { useAuthContext } from '../../context/authContext';

export function Header() {
  const { user, role, isAdmin, signOut } = useAuthContext();

  return (
    <header className="h-16 bg-[var(--color-surface-800)] border-b border-[var(--color-glass-border)] flex items-center justify-between px-6 shrink-0">
      {/* Logo */}
      <div className="flex items-center gap-3">
        <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyan-400 to-emerald-400 flex items-center justify-center">
          <span className="text-white font-bold text-sm">RG</span>
        </div>
        <div>
          <h1 className="text-white font-semibold text-sm leading-tight">
            RoadGuard
          </h1>
          <p className="text-slate-500 text-xs">Operator Portal</p>
        </div>
      </div>

      {/* User info */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 text-sm">
          {isAdmin ? (
            <ShieldCheck className="w-4 h-4 text-[var(--color-accent-primary)]" />
          ) : (
            <Shield className="w-4 h-4 text-[var(--color-accent-secondary)]" />
          )}
          <span className="text-slate-300 hidden sm:inline">
            {user?.email}
          </span>
          <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--color-surface-700)] text-[var(--color-accent-primary)] uppercase">
            {role}
          </span>
        </div>

        <button
          onClick={signOut}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-[var(--color-surface-700)] transition-colors"
          title="Sign out"
        >
          <LogOut className="w-4 h-4" />
          <span className="hidden sm:inline">Sign Out</span>
        </button>
      </div>
    </header>
  );
}
