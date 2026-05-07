/**
 * Error Boundary for dashboard and its components.
 * Catches rendering errors and displays a recovery UI instead of crashing.
 */

import { Component, type ReactNode } from "react";
import { AlertCircle } from "lucide-react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class DashboardErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error) {
    console.error("[DashboardErrorBoundary] Error caught:", error);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="h-screen flex flex-col bg-surface-900">
          <div className="flex-1 flex items-center justify-center p-4">
            <div className="glass-card max-w-md text-center">
              <AlertCircle className="w-16 h-16 text-accent-danger mx-auto mb-4" />
              <h2 className="text-xl font-semibold text-white mb-2">
                Dashboard Error
              </h2>
              <p className="text-sm text-slate-400 mb-4">
                An unexpected error occurred while loading the dashboard. This
                is often due to Firestore emulator connection issues or missing
                data.
              </p>
              <p className="text-xs text-slate-500 mb-4 bg-surface-800 p-3 rounded font-mono wrap-break-word">
                {this.state.error?.message || "Unknown error"}
              </p>
              <button
                onClick={() => window.location.reload()}
                className="px-4 py-2 bg-accent-primary text-white rounded-lg text-sm font-medium hover:bg-accent-primary/80 transition-colors"
              >
                Reload Page
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
