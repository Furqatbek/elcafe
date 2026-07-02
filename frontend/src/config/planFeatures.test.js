import { describe, it, expect } from 'vitest';
import { featureForPath } from './planFeatures';

// Mirrors the backend PlanFeatureGuardInterceptor map — these assertions catch frontend/backend drift.
describe('featureForPath', () => {
  it('maps paid sidebar paths to their feature code', () => {
    expect(featureForPath('/kitchen/inventory')).toBe('inventory');
    expect(featureForPath('/kitchen/production')).toBe('kitchen.production');
    expect(featureForPath('/kitchen/po-suggestions')).toBe('inventory.po_suggestions');
    expect(featureForPath('/finance/payroll')).toBe('payroll');
    expect(featureForPath('/finance/expenses')).toBe('finance');
    expect(featureForPath('/marketing/sms')).toBe('marketing.sms');
    expect(featureForPath('/marketing/promotions')).toBe('marketing');
    expect(featureForPath('/dashboard/financial-analytics')).toBe('analytics');
    expect(featureForPath('/orders/self-service')).toBe('orders.online');
    expect(featureForPath('/settings/telegram-subscribers')).toBe('telegram.subscribers');
  });

  it('gates the legacy alias routes like their /marketing homes', () => {
    expect(featureForPath('/promotions')).toBe('marketing');
    expect(featureForPath('/coupons')).toBe('marketing');
    expect(featureForPath('/happy-hours')).toBe('marketing');
    expect(featureForPath('/bundles')).toBe('marketing');
    expect(featureForPath('/referrals')).toBe('marketing.referrals');
  });

  it('returns null for core (always-visible) paths, including /subscription', () => {
    expect(featureForPath('/orders')).toBeNull();
    expect(featureForPath('/orders/history')).toBeNull();
    expect(featureForPath('/products')).toBeNull();
    expect(featureForPath('/customers')).toBeNull();
    expect(featureForPath('/system-users')).toBeNull();
    expect(featureForPath('/settings/printers')).toBeNull();
    expect(featureForPath('/subscription')).toBeNull();
    expect(featureForPath('/pos')).toBeNull();
  });
});
