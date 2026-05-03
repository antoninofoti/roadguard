/**
 * Top header bar with navigation tabs and user info.
 *
 * Navigation:
 *  - Dashboard (/) — always visible
 *  - Analytics (/analytics) — always visible (operators already logged in)
 *  - User info + Sign out
 */

import { LogOut, Shield, ShieldCheck, LayoutDashboard, BarChart2 } from 'lucide-react';
import { NavLink } from 'react-router-dom';
import { useAuthContext } from '../../context/authContext';

export function Header() {
  const { user, role, isAdmin, signOut } = useAuthContext();

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm transition-colors ${
      isActive
        ? 'bg-[var(--color-surface-700)] text-white font-medium'
        : 'text-slate-400 hover:text-white hover:bg-[var(--color-surface-700)]'
    }`;

  return (
    <header
      className="h-16 bg-[var(--color-surface-800)] border-b border-[var(--color-glass-border)] flex items-center justify-between px-6 shrink-0"
      role="banner"
    >
      {/* Logo + Nav */}
      <div className="flex items-center gap-6">
        {/* Logo */}
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyan-400 to-emerald-400 flex items-center justify-center" aria-hidden="true">
            <span className="text-white font-bold text-sm">RG</span>
          </div>
          <div className="hidden sm:block">
            <h1 className="text-white font-semibold text-sm leading-tight">RoadGuard</h1>
            <p className="text-slate-500 text-xs">Operator Portal</p>
          </div>
        </div>

        {/* Nav tabs */}
        <nav className="flex items-center gap-1" aria-label="Main navigation">
          <NavLink to="/" end className={navLinkClass} title="Dashboard">
            <LayoutDashboard className="w-4 h-4" aria-hidden="true" />
            <span className="hidden md:inline">Dashboard</span>
          </NavLink>
          <NavLink to="/analytics" className={navLinkClass} title="Analytics">
            <BarChart2 className="w-4 h-4" aria-hidden="true" />
            <span className="hidden md:inline">Analytics</span>
          </NavLink>
        </nav>
      </div>

      {/* User info + sign out */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 text-sm" aria-label={`Logged in as ${user?.email}, role: ${role}`}>
          {isAdmin ? (
            <ShieldCheck className="w-4 h-4 text-[var(--color-accent-primary)]" aria-hidden="true" />
          ) : (
            <Shield className="w-4 h-4 text-[var(--color-accent-secondary)]" aria-hidden="true" />
          )}
          <span className="text-slate-300 hidden sm:inline">{user?.email}</span>
          <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--color-surface-700)] text-[var(--color-accent-primary)] uppercase">
            {role}
          </span>
        </div>

        <button
          onClick={signOut}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-[var(--color-surface-700)] transition-colors"
          title="Sign out"
          aria-label="Sign out"
        >
          <LogOut className="w-4 h-4" aria-hidden="true" />
          <span className="hidden sm:inline">Sign Out</span>
        </button>
      </div>
    </header>
  );
}
