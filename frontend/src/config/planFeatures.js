// Frontend mirror of the backend feature gating (com.elcafe.modules.billing.PlanFeatures +
// PlanFeatureGuardInterceptor). A sidebar/route path maps to the feature code required to see it;
// core paths are absent here (always visible). The sidebar filters items by this, and Layout guards
// the routed page against it. Keep in sync with the backend and V157 seed.
const PATH_FEATURE = {
  // Dashboard analytics (Advance)
  '/dashboard': 'analytics',
  '/dashboard/financial-analytics': 'analytics',
  '/dashboard/operational-analytics': 'analytics',
  '/dashboard/customer-analytics': 'analytics',
  '/dashboard/inventory-analytics': 'analytics',
  // Orders
  '/orders/self-service': 'orders.online',
  // Restaurant
  '/restaurants/reservations': 'reservations',
  // Clients
  '/customer-segments': 'customers.segments',
  // Employees
  '/employees/waiter-performance': 'staff.performance',
  '/employees/consumption': 'staff.consumption',
  '/employees/consumption-allowances': 'staff.consumption',
  '/couriers': 'couriers',
  '/courier-map': 'couriers',
  // Catalog
  '/menu-collections': 'menu.collections',
  // Kitchen
  '/kitchen': 'kitchen',
  '/kitchen/inventory': 'inventory',
  '/kitchen/recipes': 'inventory',
  '/kitchen/expiry': 'inventory',
  '/kitchen/stock-counts': 'inventory',
  '/kitchen/waste': 'inventory',
  '/kitchen/suppliers': 'inventory',
  '/kitchen/stock-alerts': 'inventory',
  '/kitchen/valuation': 'inventory',
  '/kitchen/po-suggestions': 'inventory.po_suggestions',
  '/kitchen/production': 'kitchen.production',
  // Marketing
  '/marketing/promotions': 'marketing',
  '/marketing/coupons': 'marketing',
  '/marketing/happy-hours': 'marketing',
  '/marketing/bundles': 'marketing',
  '/marketing/qr-codes': 'marketing',
  '/marketing/referrals': 'marketing.referrals',
  '/marketing/sms': 'marketing.sms',
  '/marketing/telegram': 'marketing.telegram',
  '/marketing/instagram': 'marketing.instagram',
  '/marketing/milestones': 'marketing.milestones',
  '/marketing/loyalty': 'loyalty',
  '/marketing/analytics': 'marketing.analytics',
  // Legacy alias routes (App.jsx keeps them for old bookmarks) — same gating as their
  // /marketing/* homes, so a direct URL can't sidestep the guard.
  '/promotions': 'marketing',
  '/coupons': 'marketing',
  '/happy-hours': 'marketing',
  '/bundles': 'marketing',
  '/referrals': 'marketing.referrals',
  '/reviews': 'reviews',
  // Finance
  '/finance/purchase-orders': 'finance',
  '/finance/expenses': 'finance',
  '/finance/reports': 'finance',
  '/finance/pricing': 'finance',
  '/finance/alerts': 'finance',
  '/finance/payroll': 'payroll',
  // Settings
  '/settings/kitchen-stations': 'kitchen.stations',
  '/settings/telegram-subscribers': 'telegram.subscribers',
};

/** The feature code required to access a path, or null if the path is core (always visible). */
export function featureForPath(path) {
  return PATH_FEATURE[path] || null;
}
