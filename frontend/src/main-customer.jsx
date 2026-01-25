import React from 'react';
import ReactDOM from 'react-dom/client';
import CustomerApp from './CustomerApp';
import './index.css';
import './i18n/config';
import branding from './config/branding';

// Set document title based on branding
document.title = branding.titles.order;

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <CustomerApp />
  </React.StrictMode>
);
