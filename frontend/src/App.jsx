import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import Layout from './components/Layout';
import SessionManager from './components/SessionManager';
import { InventoryProvider } from './context/InventoryContext';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Orders from './pages/Orders';
import Restaurants from './pages/Restaurants';
import Tables from './pages/Tables';
import WorkingHours from './pages/WorkingHours';
import Products from './pages/Products';
import LinkedItems from './pages/LinkedItems';
import Menu from './pages/Menu';
import Categories from './pages/Categories';
import Customers from './pages/Customers';
import CustomerSegments from './pages/CustomerSegments';
import Operators from './pages/Operators';
import Waiters from './pages/Waiters';
import Couriers from './pages/Couriers';
import CourierMap from './pages/CourierMap';
import MenuCollections from './pages/MenuCollections';
import KitchenDashboard from './pages/KitchenDashboard';
import FinancialAnalytics from './pages/FinancialAnalytics';
import OperationalAnalytics from './pages/OperationalAnalytics';
import CustomerAnalytics from './pages/CustomerAnalytics';
import InventoryAnalytics from './pages/InventoryAnalytics';
import PurchaseOrders from './pages/PurchaseOrders';
import POSuggestions from './pages/POSuggestions';
import Expenses from './pages/Expenses';
import FinancialReports from './pages/FinancialReports';
import PrinterSettings from './pages/PrinterSettings';
import PricingDashboard from './pages/PricingDashboard';
import FinancialAlerts from './pages/FinancialAlerts';
import Promotions from './pages/Promotions';
import CouponCodes from './pages/CouponCodes';
import HappyHours from './pages/HappyHours';
import {
  InventoryIngredients,
  InventoryRecipes,
  InventoryExpiry,
  InventoryStockCounts,
  InventoryWaste,
  InventorySuppliers,
  InventoryAlerts,
  InventoryValuation,
} from './pages/inventory';
import POSApp from './pos/POSApp';

function PrivateRoute({ children }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return isAuthenticated ? children : <Navigate to="/login" />;
}

// Role-based route guard - blocks certain routes for OPERATOR role
function AdminRoute({ children }) {
  const user = useAuthStore((state) => state.user);
  const isOperator = user?.role === 'OPERATOR';

  // If user is OPERATOR, redirect to orders page
  if (isOperator) {
    return <Navigate to="/orders" replace />;
  }

  return children;
}

function InventoryWrapper({ children }) {
  return <InventoryProvider>{children}</InventoryProvider>;
}

function App() {
  return (
    <BrowserRouter basename="/admin">
      <SessionManager />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/"
          element={
            <PrivateRoute>
              <Layout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="/orders" replace />} />
          <Route path="dashboard" element={<AdminRoute><Dashboard /></AdminRoute>} />
          <Route path="dashboard/financial-analytics" element={<AdminRoute><FinancialAnalytics /></AdminRoute>} />
          <Route path="dashboard/operational-analytics" element={<AdminRoute><OperationalAnalytics /></AdminRoute>} />
          <Route path="dashboard/customer-analytics" element={<AdminRoute><CustomerAnalytics /></AdminRoute>} />
          <Route path="dashboard/inventory-analytics" element={<AdminRoute><InventoryAnalytics /></AdminRoute>} />
          <Route path="orders" element={<Orders />} />
          <Route path="restaurants" element={<Restaurants />} />
          <Route path="restaurants/tables" element={<Tables />} />
          <Route path="restaurants/working-hours" element={<WorkingHours />} />
          <Route path="products" element={<Products />} />
          <Route path="products/:productId/linked-items" element={<LinkedItems />} />
          <Route path="menu" element={<Menu />} />
          <Route path="catalog/categories" element={<Categories />} />
          <Route path="menu-collections" element={<MenuCollections />} />
          <Route path="customers" element={<Customers />} />
          <Route path="customer-segments" element={<CustomerSegments />} />
          <Route path="operators" element={<Operators />} />
          <Route path="employees/waiters" element={<Waiters />} />
          <Route path="couriers" element={<Couriers />} />
          <Route path="courier-map" element={<CourierMap />} />
          <Route path="kitchen" element={<KitchenDashboard />} />
          {/* Inventory Routes - each wrapped with InventoryProvider */}
          <Route path="kitchen/inventory" element={<InventoryWrapper><InventoryIngredients /></InventoryWrapper>} />
          <Route path="kitchen/recipes" element={<InventoryWrapper><InventoryRecipes /></InventoryWrapper>} />
          <Route path="kitchen/expiry" element={<InventoryWrapper><InventoryExpiry /></InventoryWrapper>} />
          <Route path="kitchen/stock-counts" element={<InventoryWrapper><InventoryStockCounts /></InventoryWrapper>} />
          <Route path="kitchen/waste" element={<InventoryWrapper><InventoryWaste /></InventoryWrapper>} />
          <Route path="kitchen/suppliers" element={<InventoryWrapper><InventorySuppliers /></InventoryWrapper>} />
          <Route path="kitchen/stock-alerts" element={<InventoryWrapper><InventoryAlerts /></InventoryWrapper>} />
          <Route path="kitchen/valuation" element={<InventoryWrapper><InventoryValuation /></InventoryWrapper>} />
          <Route path="kitchen/po-suggestions" element={<POSuggestions />} />
          <Route path="finance/purchase-orders" element={<AdminRoute><PurchaseOrders /></AdminRoute>} />
          <Route path="finance/expenses" element={<AdminRoute><Expenses /></AdminRoute>} />
          <Route path="finance/reports" element={<AdminRoute><FinancialReports /></AdminRoute>} />
          <Route path="finance/pricing" element={<AdminRoute><PricingDashboard /></AdminRoute>} />
          <Route path="finance/alerts" element={<AdminRoute><FinancialAlerts /></AdminRoute>} />
          <Route path="marketing/promotions" element={<AdminRoute><Promotions /></AdminRoute>} />
          <Route path="marketing/coupons" element={<AdminRoute><CouponCodes /></AdminRoute>} />
          <Route path="marketing/happy-hours" element={<AdminRoute><HappyHours /></AdminRoute>} />
          <Route path="promotions" element={<AdminRoute><Promotions /></AdminRoute>} />
          <Route path="coupons" element={<AdminRoute><CouponCodes /></AdminRoute>} />
          <Route path="happy-hours" element={<AdminRoute><HappyHours /></AdminRoute>} />
          <Route path="settings/printers" element={<PrinterSettings />} />
          <Route path="pos" element={<POSApp />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
