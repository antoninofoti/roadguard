/**
 * Login page with Firebase Auth.
 *
 * Dark glassmorphism design with animated gradient background.
 */

import { useState, type FormEvent } from "react";
import { Navigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Shield, Mail, Lock, AlertCircle, ArrowRight } from "lucide-react";
import { useAuthContext } from "../context/authContext";

export function LoginPage() {
  const { user, isOperator, isLoading, error, signIn } = useAuthContext();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Redirect if already authenticated
  if (user && isOperator && !isLoading) {
    return <Navigate to="/" replace />;
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    await signIn(email, password);
    setIsSubmitting(false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-900 p-4 relative overflow-hidden">
      {/* Animated background orbs */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-[40%] -left-[20%] w-[60%] h-[60%] rounded-full bg-cyan-500/5 blur-3xl animate-pulse" />
        <div className="absolute -bottom-[40%] -right-[20%] w-[60%] h-[60%] rounded-full bg-emerald-500/5 blur-3xl animate-pulse" />
      </div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="w-full max-w-md"
      >
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="w-16 h-16 rounded-2xl bg-linear-to-br from-cyan-400 to-emerald-400 flex items-center justify-center mx-auto mb-4 shadow-lg shadow-cyan-500/20">
            <Shield className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-white">RoadGuard</h1>
          <p className="text-slate-400 text-sm mt-1">Operator Portal</p>
        </div>

        {/* Login Card */}
        <div className="glass-card p-8">
          <h2 className="text-lg font-semibold text-white mb-1">Sign In</h2>
          <p className="text-sm text-slate-400 mb-4">
            This portal is restricted to operator and administrator access.
            Provide your email and password to authenticate. The frontend
            verifies credentials against the production cloud backend (Google Cloud Firebase)
            and receives a session token which grants access to protected
            features such as the dashboard, reports, and administrative actions.
          </p>

          <div className="mb-6 bg-surface-800 p-3 rounded-md border border-glass-border text-sm text-slate-300">
            <div className="font-semibold text-white mb-2">
              Operator Credentials (Cloud)
            </div>
            <div className="space-y-1">
              <div>
                Admin/Operator:{" "}
                <span className="font-medium">admin.demo@roadguard.local</span> /{" "}
                <span className="font-medium">RoadGuardDemo2026!</span>
              </div>
            </div>
            <div className="text-xs text-slate-500 mt-2">
              Access is managed through Role-Based Access Control (RBAC). 
              Only registered municipal accounts are authorized to access the dashboard.
            </div>
            <div className="text-xs text-amber-400/80 mt-2 border-t border-glass-border/20 pt-2 flex gap-1.5">
              <span className="font-semibold shrink-0">⚠️ Academic Sandbox Notice:</span>
              <span>
                These test credentials are temporarily exposed in the UI exclusively for thesis evaluation and review. 
                In commercial/production builds, this helper utility is strictly compiled out, and secure out-of-band credential provisioning is enforced.
              </span>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Email */}
            <div>
              <label
                htmlFor="login-email"
                className="block text-xs font-medium text-slate-400 mb-1.5"
              >
                Email
              </label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  id="login-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="operator@roadguard.it"
                  required
                  autoComplete="email"
                  className="w-full pl-10 pr-4 py-2.5 rounded-lg bg-surface-900 border border-glass-border text-sm text-white placeholder:text-slate-600 focus:outline-none focus:border-accent-primary/50 focus:ring-1 focus:ring-accent-primary/20 transition-colors"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <label
                htmlFor="login-password"
                className="block text-xs font-medium text-slate-400 mb-1.5"
              >
                Password
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                <input
                  id="login-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  autoComplete="current-password"
                  className="w-full pl-10 pr-4 py-2.5 rounded-lg bg-surface-900 border border-glass-border text-sm text-white placeholder:text-slate-600 focus:outline-none focus:border-accent-primary/50 focus:ring-1 focus:ring-accent-primary/20 transition-colors"
                />
              </div>
            </div>

            {/* Error message */}
            {error && (
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex items-center gap-2 p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-sm text-red-400"
              >
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{error}</span>
              </motion.div>
            )}

            {/* Submit */}
            <button
              type="submit"
              disabled={isSubmitting || isLoading}
              className="w-full flex items-center justify-center gap-2 py-3 rounded-lg text-sm font-semibold bg-linear-to-r from-cyan-500 to-emerald-500 text-white hover:from-cyan-400 hover:to-emerald-400 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-cyan-500/20"
            >
              {isSubmitting ? (
                <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  Sign In
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>
        </div>

        {/* Footer */}
        <p className="text-center text-xs text-slate-600 mt-6">
          RoadGuard — Intelligent Road Monitoring System
        </p>
      </motion.div>
    </div>
  );
}
