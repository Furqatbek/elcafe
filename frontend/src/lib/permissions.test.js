import { describe, it, expect } from 'vitest';
import { can, isSuperAdmin, isOperator } from './permissions';

// Pins EH-2.8: the UI role gate mirrors the backend's authorization so actions a role can't perform
// are hidden rather than clicked-through to a 403. Unknown actions/roles fail closed.

describe('can()', () => {
  it('platform + platform-marketing + restaurant-binding are SUPER_ADMIN only', () => {
    for (const action of ['platform.manage', 'marketing.platform', 'systemUser.bindRestaurant']) {
      expect(can({ role: 'SUPER_ADMIN' }, action)).toBe(true);
      for (const role of ['ADMIN', 'OWNER', 'MANAGER', 'OPERATOR']) {
        expect(can({ role }, action)).toBe(false);
      }
    }
  });

  it('dashboard/finance/marketing views are hidden from OPERATOR, shown to everyone else', () => {
    for (const action of ['dashboard.view', 'finance.view', 'marketing.view']) {
      expect(can('OPERATOR', action)).toBe(false);
      for (const role of ['SUPER_ADMIN', 'ADMIN', 'OWNER', 'MANAGER']) {
        expect(can(role, action)).toBe(true);
      }
    }
  });

  it('finance.manage / restaurant.manage are admin/owner and up', () => {
    expect(can('OWNER', 'finance.manage')).toBe(true);
    expect(can('MANAGER', 'finance.manage')).toBe(false);
    expect(can('MANAGER', 'restaurant.manage')).toBe(false);
    expect(can('ADMIN', 'restaurant.manage')).toBe(true);
  });

  it('fails closed on unknown action, missing role, or null user', () => {
    expect(can('SUPER_ADMIN', 'nope.unknown')).toBe(false);
    expect(can({ role: undefined }, 'dashboard.view')).toBe(false);
    expect(can(null, 'dashboard.view')).toBe(false);
  });

  it('accepts a bare role string or a user object', () => {
    expect(can('SUPER_ADMIN', 'platform.manage')).toBe(true);
    expect(can({ role: 'SUPER_ADMIN' }, 'platform.manage')).toBe(true);
  });

  it('convenience helpers', () => {
    expect(isSuperAdmin({ role: 'SUPER_ADMIN' })).toBe(true);
    expect(isSuperAdmin('ADMIN')).toBe(false);
    expect(isOperator('OPERATOR')).toBe(true);
  });
});
