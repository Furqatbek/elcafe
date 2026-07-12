import { test, expect } from '@playwright/test';

/**
 * EH-4.3 (docs/ERROR_HANDLING_PLAN.md): the error pipeline exercised end-to-end in a real browser —
 * axios interceptor → normalized AppError → localized errorMessage → <QueryState> / notifyError /
 * the session-ended banner. Backend stubbed at the network layer (per plan-gating.spec.js), so no
 * Spring app is needed; responses are the standard EH-0 envelope.
 */

function seedSuperAdminSession() {
  const now = Date.now();
  localStorage.setItem('access_token', 'e2e.fake.token');
  localStorage.setItem('refresh_token', 'e2e.fake.refresh');
  localStorage.setItem('access_token_expiry', String(now + 60 * 60 * 1000));
  localStorage.setItem('refresh_token_expiry', String(now + 24 * 60 * 60 * 1000));
  localStorage.setItem('token_set_time', String(now));
  localStorage.setItem('user', JSON.stringify({
    id: 1, email: 'ops@e2e.test', firstName: 'E2E', lastName: 'Ops', role: 'SUPER_ADMIN', restaurantId: null,
  }));
  localStorage.setItem('language', 'en');
}

const envelope = (error, message, extra = {}) => ({ success: false, error, message, requestId: 'e2e-req-1', ...extra });

test('session ended → login shows the localized "session ended" banner, not a silent bounce', async ({ page }) => {
  // The api layer stashes this when refresh is exhausted; the Login page reads it on mount.
  await page.addInitScript(() => {
    localStorage.setItem('language', 'en');
    sessionStorage.setItem('auth_logout_reason', 'SESSION_ENDED');
  });
  await page.goto('/admin/login');

  await expect(page.getByText('Your session has ended. Please sign in again.')).toBeVisible();
});

test('list load 500 → QueryState error + Retry, and retry recovers (no blank table)', async ({ page }) => {
  await page.addInitScript(seedSuperAdminSession);

  // Gate on a flag, not a call counter: React 18 StrictMode double-invokes the mount effect, so the
  // initial fetch fires twice. A counter would let the second (recovery) response win on mount and
  // the error state would never render. The flag keeps every load failing until we flip it, which we
  // only do once the error card is on screen — so the retry click is what recovers.
  let failUsers = true;
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    if (url.includes('/billing/me')) return route.fulfill({ json: { data: { planCode: 'pro', featureCodes: [] } } });
    if (url.includes('/system-users')) {
      return failUsers
        ? route.fulfill({ status: 500, json: envelope('INTERNAL', 'An unexpected error occurred') })
        : route.fulfill({ json: { data: [{ id: 1, email: 'a@t.co', firstName: 'A', lastName: 'B', role: 'ADMIN', active: true, restaurantId: 1 }] } });
    }
    return route.fulfill({ json: { data: [] } });
  });

  await page.goto('/admin/system-users');

  // First load failed → error state with a Retry, NOT a blank table.
  const retry = page.getByRole('button', { name: 'Try again' });
  await expect(retry).toBeVisible();
  // The 5xx renders the generic text + the request id — never a raw stack.
  await expect(page.getByText(/Error ID: e2e-req-1/)).toBeVisible();

  failUsers = false;   // the backend recovers; the retry click is the load that succeeds
  await retry.click();
  await expect(page.getByText('a@t.co')).toBeVisible();
});

test('403 TENANT_ACCESS_DENIED renders the localized message, never the raw code', async ({ page }) => {
  await page.addInitScript(seedSuperAdminSession);
  await page.route('**/api/v1/**', async (route) => {
    const url = route.request().url();
    if (url.includes('/billing/me')) return route.fulfill({ json: { data: { planCode: 'pro', featureCodes: [] } } });
    if (url.includes('/system-users')) {
      return route.fulfill({ status: 403, json: envelope('TENANT_ACCESS_DENIED', "You do not have access to this restaurant's data.") });
    }
    return route.fulfill({ json: { data: [] } });
  });

  await page.goto('/admin/system-users');

  await expect(page.getByText(/another restaurant/i)).toBeVisible();     // localized
  await expect(page.getByText('TENANT_ACCESS_DENIED')).toHaveCount(0);   // raw code never shown
});
