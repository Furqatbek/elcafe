/**
 * EH-2.8 (docs/ERROR_HANDLING_PLAN.md): one place that answers "may this role do X?", so the UI
 * can hide or disable actions a role can't perform instead of letting the user click through to a
 * 403. The BACKEND is still authoritative (@PreAuthorize / tenant + subscription filters); this is
 * a UX layer that mirrors those rules to avoid dead-end clicks.
 *
 * Rules are derived from the existing gates: OPERATOR is blocked from dashboard/finance/marketing
 * (App.jsx AdminRoute), the platform console and platform-operated marketing are SUPER_ADMIN-only
 * (App.jsx SuperAdminRoute / Layout superAdminOnly), and moving a user between restaurants is a
 * SUPER_ADMIN action (SystemUserController). Unknown actions fail closed (deny).
 *
 * Keep this in sync with backend authorization — a mismatch that HIDES an allowed action is a
 * usability bug; one that SHOWS a denied action just yields the (now localized) 403.
 */

const ALL = ['SUPER_ADMIN', 'ADMIN', 'OWNER', 'MANAGER', 'OPERATOR'];
const not = (...roles) => ALL.filter((r) => !roles.includes(r));

/** action key -> list of roles allowed to perform it. */
const RULES = {
  // Platform operator only
  'platform.manage': ['SUPER_ADMIN'],
  'marketing.platform': ['SUPER_ADMIN'],
  'systemUser.bindRestaurant': ['SUPER_ADMIN'],

  // Everyone except OPERATOR (mirrors AdminRoute)
  'dashboard.view': not('OPERATOR'),
  'finance.view': not('OPERATOR'),
  'marketing.view': not('OPERATOR'),

  // Management-and-up
  'systemUser.manage': ['SUPER_ADMIN', 'ADMIN', 'OWNER', 'MANAGER'],
  'restaurant.manage': ['SUPER_ADMIN', 'ADMIN', 'OWNER'],
  'finance.manage': ['SUPER_ADMIN', 'ADMIN', 'OWNER'],
};

/** Resolve a role string from a user object or a bare role string. */
function roleOf(userOrRole) {
  return typeof userOrRole === 'string' ? userOrRole : userOrRole?.role;
}

/**
 * @param {object|string} user - the auth user (or a role string)
 * @param {string} action - a key from RULES
 * @returns {boolean} whether the role may perform the action (fail-closed on unknown action/role)
 */
export function can(user, action) {
  const role = roleOf(user);
  if (!role) return false;
  const allowed = RULES[action];
  return Array.isArray(allowed) ? allowed.includes(role) : false;
}

export const isSuperAdmin = (user) => roleOf(user) === 'SUPER_ADMIN';
export const isOperator = (user) => roleOf(user) === 'OPERATOR';
