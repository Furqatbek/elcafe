import { describe, it, expect, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePlan } from './usePlan';
import { useAuthStore } from '../store/authStore';

// usePlan is a pure selector over authStore.plan (the GET /billing/me payload), so we drive it by
// seeding the store directly rather than mocking the network. Store writes go through act() because
// they synchronously re-render any subscribed component.
const setPlan = (plan) => act(() => useAuthStore.setState({ plan }));

afterEach(() => {
  setPlan(null);
});

describe('usePlan', () => {
  it('returns safe defaults when no plan is loaded', () => {
    const { result } = renderHook(() => usePlan());
    expect(result.current.plan).toBeNull();
    expect(result.current.planCode).toBeNull();
    expect(result.current.planName).toBeNull();
    expect(result.current.features).toEqual([]);
    expect(result.current.hasFeature('inventory')).toBe(false);
    expect(result.current.daysUntilExpiry).toBeNull();
    expect(result.current.isTrial).toBe(false);
    expect(result.current.inGracePeriod).toBe(false);
    expect(result.current.isReadOnly).toBe(false);
  });

  it('derives fields from the loaded plan, and hasFeature checks membership', () => {
    setPlan({
      planCode: 'pro',
      planName: 'Pro',
      featureCodes: ['inventory', 'payroll', 'marketing.sms'],
      daysUntilExpiry: 5,
      isTrial: true,
      inGracePeriod: false,
      readOnly: false,
    });
    const { result } = renderHook(() => usePlan());
    expect(result.current.planCode).toBe('pro');
    expect(result.current.planName).toBe('Pro');
    expect(result.current.features).toEqual(['inventory', 'payroll', 'marketing.sms']);
    expect(result.current.hasFeature('inventory')).toBe(true);
    expect(result.current.hasFeature('reservations')).toBe(false);
    expect(result.current.daysUntilExpiry).toBe(5);
    expect(result.current.isTrial).toBe(true);
  });

  it('coerces read-only / grace flags to booleans and tolerates a missing feature list', () => {
    setPlan({ planCode: 'start', readOnly: true, inGracePeriod: true });
    const { result } = renderHook(() => usePlan());
    expect(result.current.isReadOnly).toBe(true);
    expect(result.current.inGracePeriod).toBe(true);
    expect(result.current.features).toEqual([]);
    expect(result.current.daysUntilExpiry).toBeNull();
  });
});
