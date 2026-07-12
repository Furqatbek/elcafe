import { describe, it, expect, vi, beforeEach } from 'vitest';
import { toAppError, errorMessage, notifyError } from './errors';
import { toast } from '../hooks/useToast';

// Pins the EH-0 frontend contract: normalization of every rejection shape, and the message policy —
// localized dictionary for auth/tenant/network codes, backend detail for specific 4xx, and a
// generic text + request id (never internals) for anything 5xx.

vi.mock('i18next', () => ({
  default: {
    t: (key, opts) => {
      const table = {
        'errors.INTERNAL': 'Something went wrong on our side. Please try again.',
        'errors.TENANT_ACCESS_DENIED': 'This belongs to another restaurant — you don\'t have access to it.',
        'errors.NETWORK_ERROR': 'Can\'t reach the server.',
        'errors.TIMEOUT': 'The request took too long.',
        'errors.BAD_REQUEST': 'Invalid request.',
        'errors.title': 'Error',
      };
      if (key === 'errors.requestIdSuffix') return ` (Error ID: ${opts.id})`;
      return table[key] ?? opts?.defaultValue ?? key;
    },
  },
}));
vi.mock('../hooks/useToast', () => ({ toast: vi.fn() }));

const axiosError = (status, data, headers = {}) => ({
  isAxiosError: true,
  response: { status, data, headers },
});

describe('toAppError', () => {
  it('maps a connection failure to retriable NETWORK_ERROR', () => {
    const app = toAppError({ code: 'ERR_NETWORK', message: 'Network Error' });
    expect(app.code).toBe('NETWORK_ERROR');
    expect(app.retriable).toBe(true);
    expect(app.status).toBe(0);
  });

  it('maps a client timeout to TIMEOUT', () => {
    expect(toAppError({ code: 'ECONNABORTED', message: 'timeout of 10000ms exceeded' }).code)
      .toBe('TIMEOUT');
  });

  it('marks canceled requests silent', () => {
    const app = toAppError({ code: 'ERR_CANCELED' });
    expect(app.code).toBe('CANCELED');
    expect(app.silent).toBe(true);
  });

  it('prefers the backend machine code over the status fallback', () => {
    const app = toAppError(axiosError(401, { error: 'TOKEN_EXPIRED', message: 'expired' }));
    expect(app.code).toBe('TOKEN_EXPIRED');
  });

  it('falls back to a status-derived code when the body has none (legacy/proxy errors)', () => {
    expect(toAppError(axiosError(403, {})).code).toBe('FORBIDDEN');
    expect(toAppError(axiosError(502, 'Bad Gateway')).code).toBe('INTERNAL');
  });

  it('extracts requestId and fieldErrors from the envelope', () => {
    const app = toAppError(axiosError(400, {
      error: 'VALIDATION_ERROR', message: 'Validation failed',
      errors: { email: 'must not be blank' }, requestId: 'req-1',
    }));
    expect(app.requestId).toBe('req-1');
    expect(app.fieldErrors).toEqual({ email: 'must not be blank' });
  });

  it('is idempotent on already-normalized errors', () => {
    const app = toAppError(axiosError(404, { error: 'NOT_FOUND' }));
    expect(toAppError(app)).toBe(app);
  });
});

describe('errorMessage policy', () => {
  it('5xx renders the generic localized text + request id, never the backend message', () => {
    const msg = errorMessage(toAppError(axiosError(500, {
      error: 'INTERNAL', message: 'NullPointerException at OrderService.java:42', requestId: 'abc-1',
    })));
    expect(msg).toContain('Something went wrong on our side');
    expect(msg).toContain('abc-1');
    expect(msg).not.toContain('NullPointerException');
  });

  it('auth/tenant codes always use the dictionary, not backend English', () => {
    const msg = errorMessage(toAppError(axiosError(403, {
      error: 'TENANT_ACCESS_DENIED', message: "You do not have access to this restaurant's data.",
    })));
    expect(msg).toContain('another restaurant');
  });

  it('specific 4xx business errors surface the backend detail', () => {
    const msg = errorMessage(toAppError(axiosError(400, {
      error: 'BAD_REQUEST', message: 'Email already in use',
    })));
    expect(msg).toBe('Email already in use');
  });
});

describe('notifyError', () => {
  beforeEach(() => vi.clearAllMocks());

  it('toasts a destructive notification with the localized text', () => {
    notifyError(axiosError(403, { error: 'TENANT_ACCESS_DENIED', message: 'raw backend text' }));
    expect(toast).toHaveBeenCalledTimes(1);
    const call = toast.mock.calls[0][0];
    expect(call.variant).toBe('destructive');
    expect(call.description).toContain('another restaurant');
  });

  it('stays silent for canceled requests', () => {
    notifyError({ code: 'ERR_CANCELED' });
    expect(toast).not.toHaveBeenCalled();
  });
});
