import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

// Separate from vite.config.js so `vite build` stays untouched.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    // Unit tests live under src/. Keep Vitest out of e2e/ — those are Playwright specs and would
    // fail to run under Vitest (they import @playwright/test).
    include: ['src/**/*.{test,spec}.{js,jsx}'],
  },
});
