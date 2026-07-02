import { describe, it, expect, beforeEach } from 'vitest';
import api from './api';
import { useSubscriptionStore } from '../store/subscriptionStore';

/**
 * Pins the frontend half of the Phase 2 402 contract: the axios response interceptor must flip the
 * suspension flag on exactly `402` + `error === 'SUBSCRIPTION_INACTIVE'` (the body written by
 * SubscriptionEnforcementFilter) and on nothing else. The backend half is pinned by
 * SubscriptionEnforcementFilterTest, so drift on either side of the string contract now fails a test.
 */

// The one response interceptor api.js registers; its rejection handler is the code under test.
const rejected = api.interceptors.response.handlers[0].rejected;

const reject = (error) => rejected({ config: {}, ...error }).catch(() => {});

beforeEach(() => useSubscriptionStore.setState({ suspended: false }));

describe('api response interceptor — 402 SUBSCRIPTION_INACTIVE', () => {
  it('flips the suspension flag on 402 SUBSCRIPTION_INACTIVE', async () => {
    await reject({ response: { status: 402, data: { error: 'SUBSCRIPTION_INACTIVE', status: 'SUSPENDED' } } });
    expect(useSubscriptionStore.getState().suspended).toBe(true);
  });

  it('ignores a 402 with a different error code', async () => {
    await reject({ response: { status: 402, data: { error: 'SOMETHING_ELSE' } } });
    expect(useSubscriptionStore.getState().suspended).toBe(false);
  });

  it('ignores a 402 with no body', async () => {
    await reject({ response: { status: 402 } });
    expect(useSubscriptionStore.getState().suspended).toBe(false);
  });

  it('ignores other statuses carrying the same error code', async () => {
    await reject({ response: { status: 403, data: { error: 'SUBSCRIPTION_INACTIVE' } } });
    expect(useSubscriptionStore.getState().suspended).toBe(false);
  });

  it('still rejects so callers see the original error', async () => {
    const error = { config: {}, response: { status: 402, data: { error: 'SUBSCRIPTION_INACTIVE' } } };
    await expect(rejected(error)).rejects.toBe(error);
  });
});
