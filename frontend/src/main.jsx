import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import CustomerApp from './CustomerApp.jsx'
import './index.css'
import { ready as i18nReady } from './i18n/config'
import branding from './config/branding'

// Check if we're on a customer-facing route
const isCustomerRoute = window.location.pathname.startsWith('/order')

// Set document title based on branding
document.title = isCustomerRoute ? branding.titles.order : branding.titles.admin

class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    console.error('[ErrorBoundary] App crashed:', error, info?.componentStack);
  }

  handleReload() {
    // Clear potentially corrupted POS store before reloading
    try { localStorage.removeItem('pos-storage'); } catch (_) {}
    window.location.reload();
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', fontFamily: 'sans-serif', gap: '16px' }}>
          <h2 style={{ fontSize: '20px', fontWeight: '600' }}>Something went wrong</h2>
          <p style={{ color: '#666' }}>An unexpected error occurred. Please reload the page.</p>
          <button
            onClick={() => this.handleReload()}
            style={{ padding: '10px 24px', background: '#2563eb', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '16px' }}
          >
            Reload
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

// Wait for i18n (active language + en fallback) before the first render so nothing flashes raw keys.
i18nReady.finally(() => {
  ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
      <ErrorBoundary>
        {isCustomerRoute ? <CustomerApp /> : <App />}
      </ErrorBoundary>
    </React.StrictMode>,
  )
})
