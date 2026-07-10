import React from 'react';
import ReactDOM from 'react-dom/client';
import CustomerApp from './CustomerApp';
import './index.css';
import { ready as i18nReady } from './i18n/config';
import branding from './config/branding';

// Set document title based on branding
document.title = branding.titles.order;

// Wait for i18n (active language + en fallback) before the first render so nothing flashes raw keys.
i18nReady.finally(() => {
  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <CustomerApp />
    </React.StrictMode>
  );
});
