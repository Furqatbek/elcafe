import { defineConfig, devices } from '@playwright/test';

// Browser-level E2E for the admin SPA. These run the real Vite dev server and a real Chromium, but
// stub the backend at the network layer (see e2e/*.spec.js) so they're deterministic and need no
// running Spring app or seeded DB. Scope is the Phase 1 plan-gating UX. Browsers live in
// PLAYWRIGHT_BROWSERS_PATH (preinstalled); run `npx playwright install chromium` if missing.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'line' : 'list',
  use: {
    baseURL: 'http://localhost:3000',
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  // The app is served under base '/admin/'; wait on that path, not '/'.
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000/admin/',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
