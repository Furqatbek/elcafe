import React from 'react';
import i18n from 'i18next';

/**
 * EH-2.2 (docs/ERROR_HANDLING_PLAN.md): a per-route error boundary. A render crash in one page
 * shows an in-shell card (the nav/header stay alive) instead of the root boundary blanking the
 * whole app. Resets automatically when the route changes (pass the path as {@code resetKey}), so
 * navigating away from a broken page recovers, and offers Try again / Back.
 *
 * i18n via the i18next singleton (not react-i18next) because a class component can't use the hook
 * and the boundary must render even if a provider above it is what crashed.
 */
export default class RouteErrorBoundary extends React.Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('[RouteErrorBoundary]', error, info?.componentStack);
  }

  componentDidUpdate(prevProps) {
    if (this.props.resetKey !== prevProps.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }

  render() {
    if (!this.state.error) return this.props.children;
    const t = (k, d) => i18n.t(k, { defaultValue: d });
    return (
      <div className="flex flex-col items-center justify-center gap-4 p-12 text-center min-h-[50vh]">
        <h2 className="text-xl font-semibold">
          {t('errors.pageCrashTitle', 'This page ran into a problem')}
        </h2>
        <p className="text-muted-foreground max-w-md">
          {t('errors.pageCrashBody',
            'Something went wrong while displaying this page. Try again, or go back and reopen it.')}
        </p>
        <div className="flex gap-3">
          <button
            onClick={() => this.setState({ error: null })}
            className="px-4 py-2 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:opacity-90"
          >
            {t('common.retry', 'Try again')}
          </button>
          <button
            onClick={() => window.history.back()}
            className="px-4 py-2 rounded-md border text-sm font-medium hover:bg-accent"
          >
            {t('common.back', 'Back')}
          </button>
        </div>
      </div>
    );
  }
}
