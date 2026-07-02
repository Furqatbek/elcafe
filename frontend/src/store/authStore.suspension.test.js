import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the API layer before authStore pulls it in — these tests exercise store wiring, not HTTP.
vi.mock('../services/api', () => ({
  authAPI: {
    login: vi.fn().mockResolvedValue({
      data: { data: { accessToken: 'at', refreshToken: 'rt', user: { id: 1, role: 'ADMIN' } } },
    }),
    register: vi.fn().mockResolvedValue({
      data: { data: { accessToken: 'at', refreshToken: 'rt', user: { id: 2, role: 'ADMIN' } } },
    }),
  },
  billingAPI: { getMe: vi.fn().mockResolvedValue({ data: { data: null } }) },
}));

import { useAuthStore } from './authStore';
import { useSubscriptionStore } from '../store/subscriptionStore';

/**
 * A suspension flag from a prior session must never survive into a fresh one — otherwise a user who
 * logs into a different (active) tenant would face a stale SuspensionGate overlay.
 */
describe('authStore clears the suspension flag', () => {
  beforeEach(() => {
    localStorage.clear();
    useSubscriptionStore.setState({ suspended: true }); // stale flag from a previous session
  });

  it('on login', async () => {
    const result = await useAuthStore.getState().login({ email: 'a@b.c', password: 'x' });
    expect(result.success).toBe(true);
    expect(useSubscriptionStore.getState().suspended).toBe(false);
  });

  it('on register', async () => {
    const result = await useAuthStore.getState().register({ email: 'a@b.c', password: 'x' });
    expect(result.success).toBe(true);
    expect(useSubscriptionStore.getState().suspended).toBe(false);
  });
});
