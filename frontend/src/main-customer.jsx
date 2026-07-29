import React from 'react';
import ReactDOM from 'react-dom/client';
import CustomerApp from './CustomerApp';
import './index.css';
import { ready as i18nReady } from './i18n/config';
import branding from './config/branding';
import { initTelegramWebApp } from './services/telegram';

// Set document title based on branding
document.title = branding.titles.order;

// If launched from a Telegram bot, announce readiness and expand to full height before first paint.
// No-op in a normal browser (the QR flow), so this is always safe to call.
initTelegramWebApp();

// Wait for i18n (active language + en fallback) before the first render so nothing flashes raw keys.
i18nReady.finally(() => {
  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <CustomerApp />
    </React.StrictMode>
  );
});
