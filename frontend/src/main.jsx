import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import CustomerApp from './CustomerApp.jsx'
import './index.css'
import './i18n/config'
import branding from './config/branding'

// Check if we're on a customer-facing route
const isCustomerRoute = window.location.pathname.startsWith('/order')

// Set document title based on branding
document.title = isCustomerRoute ? branding.titles.order : branding.titles.admin

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {isCustomerRoute ? <CustomerApp /> : <App />}
  </React.StrictMode>,
)
