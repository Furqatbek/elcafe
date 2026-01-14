import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import CustomerApp from './CustomerApp.jsx'
import './index.css'
import './i18n/config'

// Check if we're on a customer-facing route
const isCustomerRoute = window.location.pathname.startsWith('/order')

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {isCustomerRoute ? <CustomerApp /> : <App />}
  </React.StrictMode>,
)
