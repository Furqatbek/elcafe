/**
 * EH-4.4 (docs/ERROR_HANDLING_PLAN.md): frontend error tracking, fully DORMANT unless
 * `VITE_SENTRY_DSN` is set — mirrors the backend's dormant-unless-DSN Sentry. The SDK is
 * dynamically imported only when a DSN exists, so a build without one carries no runtime cost.
 *
 * Correlation: server errors already return a `requestId` (EH-0); `captureError` tags the Sentry
 * event with it, so the "Error ID" in the user's toast maps to both the frontend Sentry event and
 * the backend log/Sentry event.
 */

let sentryRef = null;

export async function initSentry() {
  const dsn = import.meta.env.VITE_SENTRY_DSN;
  if (!dsn) return; // no DSN → Sentry stays off, SDK never loaded
  try {
    const Sentry = await import('@sentry/react');
    Sentry.init({
      dsn,
      environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || 'production',
      tracesSampleRate: 0,        // errors only — no performance tracing for now
      sendDefaultPii: false,      // mirror the backend: never attach PII
      replaysSessionSampleRate: 0,
      replaysOnErrorSampleRate: 0,
    });
    sentryRef = Sentry;
  } catch (e) {
    // Sentry must never break the app it's observing.
    console.error('[sentry] init failed', e);
  }
}

/** Report an exception (no-op until initSentry runs with a DSN). Tags with requestId when present. */
export function captureError(error, requestId) {
  if (!sentryRef) return;
  try {
    sentryRef.captureException(error, requestId ? { tags: { requestId } } : undefined);
  } catch (_) { /* swallow — observability must not throw */ }
}
