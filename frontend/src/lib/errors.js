// The i18next core singleton, NOT ../i18n/config: the app entrypoints initialize that same
// instance, and importing the config here would drag its full init (react-i18next, locale
// fetching) into the module graph of api.js — breaking every test that mocks react-i18next.
// Pre-init (degenerate case) t() returns undefined and we fall back to the English defaults.
import i18n from 'i18next';
import { toast } from '../hooks/useToast';

/**
 * EH-0.5/0.6/0.7 (docs/ERROR_HANDLING_PLAN.md): the single pipeline from any failed request to a
 * localized, human message.
 *
 *   toAppError(axiosError) -> { code, status, message, requestId, retriable, fieldErrors, silent }
 *   errorMessage(appError) -> localized string (en/ru/uz) from the errors.<CODE> dictionary
 *   notifyError(err)       -> toast it (and return the AppError)
 *
 * The api.js interceptor attaches the normalized form as `error.app` on every rejection, so pages
 * can `catch (e) { notifyError(e) }` and never touch `e.response.data.message` directly.
 */

/** Codes whose localized dictionary text always wins — raw backend English must not surface. */
const PREFER_LOCALIZED = new Set([
  'UNAUTHENTICATED', 'TOKEN_EXPIRED', 'INVALID_CREDENTIALS', 'ACCOUNT_LOCKED',
  'FORBIDDEN', 'TENANT_ACCESS_DENIED', 'SUBSCRIPTION_INACTIVE', 'RATE_LIMITED',
  'NETWORK_ERROR', 'TIMEOUT', 'CANCELED', 'METHOD_NOT_ALLOWED', 'UNSUPPORTED_MEDIA_TYPE',
  'FILE_TOO_LARGE', 'CONCURRENT_MODIFICATION',
]);

const RETRIABLE = new Set(['NETWORK_ERROR', 'TIMEOUT', 'RATE_LIMITED', 'CONCURRENT_MODIFICATION', 'INTERNAL']);

function codeFromStatus(status) {
  if (status >= 500) return 'INTERNAL';
  switch (status) {
    case 400: return 'BAD_REQUEST';
    case 401: return 'UNAUTHENTICATED';
    case 402: return 'PAYMENT_FAILED';
    case 403: return 'FORBIDDEN';
    case 404: return 'NOT_FOUND';
    case 409: return 'CONFLICT';
    case 413: return 'FILE_TOO_LARGE';
    case 415: return 'UNSUPPORTED_MEDIA_TYPE';
    case 429: return 'RATE_LIMITED';
    default: return 'BAD_REQUEST';
  }
}

/** Normalize any axios rejection (or plain Error) into the AppError shape. Idempotent. */
export function toAppError(error) {
  if (error?.isAppError) return error;

  // Canceled requests are not failures the user should hear about.
  if (error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError') {
    return { isAppError: true, code: 'CANCELED', status: 0, silent: true, retriable: false, raw: error };
  }

  const response = error?.response;
  if (!response) {
    const timedOut = error?.code === 'ECONNABORTED' || /timeout/i.test(error?.message || '');
    const code = timedOut ? 'TIMEOUT' : 'NETWORK_ERROR';
    return { isAppError: true, code, status: 0, message: null, requestId: null,
             retriable: true, fieldErrors: null, silent: false, raw: error };
  }

  const data = response.data || {};
  const status = response.status;
  const code = typeof data.error === 'string' && data.error ? data.error : codeFromStatus(status);
  const fieldErrors = data.errors && typeof data.errors === 'object' && !Array.isArray(data.errors)
    ? data.errors : null;

  return {
    isAppError: true,
    code,
    status,
    message: typeof data.message === 'string' ? data.message : null,
    requestId: data.requestId || response.headers?.['x-request-id'] || null,
    retriable: RETRIABLE.has(code) || status >= 500,
    fieldErrors,
    silent: false,
    raw: error,
  };
}

/** The AppError for a rejection, using the interceptor-attached one when present. */
export function getAppError(error) {
  return error?.app || toAppError(error);
}

function dict(code) {
  const key = `errors.${code}`;
  const value = i18n.t(key, { defaultValue: '' });
  return value || null;
}

/**
 * Localized human text for an AppError.
 *  - 5xx / INTERNAL / server faults: always the generic localized text + request id — a backend
 *    stack/internal message must never render.
 *  - PREFER_LOCALIZED codes (auth, tenant, rate limit, network…): always dictionary text.
 *  - other 4xx (BAD_REQUEST, VALIDATION_ERROR, CONFLICT, NOT_FOUND…): the backend message is the
 *    specific, human-written detail ("Email already in use") — use it, falling back to dictionary.
 */
export function errorMessage(errOrApp) {
  const app = getAppError(errOrApp);
  const generic = dict('INTERNAL') || 'Something went wrong. Please try again.';

  if (app.status >= 500 || app.code === 'INTERNAL' || app.code === 'ANALYTICS_FAILED') {
    const suffix = app.requestId
      ? i18n.t('errors.requestIdSuffix', { id: app.requestId, defaultValue: ' (Error ID: {{id}})' })
      : '';
    return (dict(app.code) || generic) + suffix;
  }
  if (PREFER_LOCALIZED.has(app.code)) {
    return dict(app.code) || app.message || generic;
  }
  return app.message || dict(app.code) || generic;
}

/**
 * Standard user notification for a failed call. Silent for canceled requests. Returns the
 * AppError so callers can still branch on `code`/`fieldErrors`.
 */
export function notifyError(error, { title } = {}) {
  const app = getAppError(error);
  if (app.silent) return app;
  toast({
    title: title || i18n.t('errors.title', { defaultValue: 'Error' }),
    description: errorMessage(app),
    variant: 'destructive',
    duration: 6000,
  });
  return app;
}

/**
 * Success toast — the non-blocking replacement for `alert('Saved')`-style confirmations
 * (EH-3, docs/ERROR_HANDLING_PLAN.md). `message` is already-resolved text (usually a t(...) call).
 */
export function notifySuccess(message, { title } = {}) {
  toast({ title, description: message, variant: 'default', duration: 4000 });
}

/**
 * Warning/validation toast — the non-blocking replacement for `alert('Please fill…')`-style
 * client-side validation messages. Destructive styling, but takes a plain message (not an error).
 */
export function notifyWarning(message, { title } = {}) {
  toast({ title, description: message, variant: 'destructive', duration: 5000 });
}
