import { test, expect } from '@playwright/test';

/**
 * Phase 1 plan-gating UX, exercised in a real browser against the real router/components — the
 * integration unit tests can't reach: PrivateRoute auth, the basename, usePlan reading GET
 * /billing/me, and Layout's route guard rendering <PlanRequired/> for an unlicensed module.
 *
 * The backend is stubbed at the network layer, so no Spring app or DB is needed. We forge an
 * authenticated session by seeding the same localStorage the auth store reads on init, then mock
 * /billing/me per tier; every other /api call returns an empty payload so pages render without a
 * backend (the gating decisions don't depend on them).
 */

const HOUR = 60 * 60 * 1000;

// Runs in the browser before app scripts, so the auth store is authenticated on first render.
function seedAuthenticatedSession() {
  const now = Date.now();
  localStorage.setItem('access_token', 'e2e.fake.token');
  localStorage.setItem('refresh_token', 'e2e.fake.refresh');
  localStorage.setItem('access_token_expiry', String(now + 60 * 60 * 1000));
  localStorage.setItem('refresh_token_expiry', String(now + 24 * 60 * 60 * 1000));
  localStorage.setItem('token_set_time', String(now));
  localStorage.setItem('user', JSON.stringify({
    id: 1, email: 'admin@e2e.test', firstName: 'E2E', role: 'ADMIN', restaurantId: 1,
  }));
  localStorage.setItem('language', 'en'); // pin i18n so wording is deterministic
}

const billing = (overrides) => ({
  restaurantId: 1,
  planCode: 'start',
  planName: 'Start',
  featureCodes: [],
  planExpiresAt: null,
  isTrial: false,
  daysUntilExpiry: null,
  inGracePeriod: false,
  readOnly: false,
  ...overrides,
});

const START = billing();
const PRO = billing({
  planCode: 'pro',
  planName: 'Pro',
  // Enough Pro codes to unlock the module under test; 'inventory' is the one the guard checks.
  featureCodes: ['analytics', 'kitchen', 'inventory', 'reservations', 'marketing', 'payroll'],
});

async function withBackend(page, billingDto) {
  await page.addInitScript(seedAuthenticatedSession);
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    if (url.includes('/billing/me')) {
      await route.fulfill({ json: { data: billingDto } });
    } else {
      // Generic empty success for everything else — keeps pages from erroring on a missing backend.
      await route.fulfill({ json: { data: [] } });
    }
  });
}

test('Start tier: a paid module is blocked by the plan guard on direct navigation', async ({ page }) => {
  await withBackend(page, START);
  await page.goto('/admin/kitchen/inventory');

  await expect(page.getByTestId('app-shell')).toBeVisible();          // authenticated shell rendered
  await expect(page.getByTestId('plan-required')).toBeVisible();       // ...and the module is gated
  await expect(page.getByRole('link', { name: 'View plans' }))
      .toHaveAttribute('href', '/admin/subscription');                 // CTA points at the plans page
});

test('Pro tier: the same paid module is allowed (no plan guard)', async ({ page }) => {
  await withBackend(page, PRO);
  await page.goto('/admin/kitchen/inventory');

  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('plan-required')).toHaveCount(0);
});

test('Core module is never gated, even on the Start tier', async ({ page }) => {
  await withBackend(page, START);
  await page.goto('/admin/orders');

  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('plan-required')).toHaveCount(0);
});
