import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import Layout from './components/Layout';
import SessionManager from './components/SessionManager';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Orders from './pages/Orders';
import Restaurants from './pages/Restaurants';
import Products from './pages/Products';
import LinkedItems from './pages/LinkedItems';
import Menu from './pages/Menu';
import Categories from './pages/Categories';
import Customers from './pages/Customers';
import CustomerSegments from './pages/CustomerSegments';
import Operators from './pages/Operators';
import Couriers from './pages/Couriers';
import CourierMap from './pages/CourierMap';
import MenuCollections from './pages/MenuCollections';
import KitchenDashboard from './pages/KitchenDashboard';
import Inventory from './pages/Inventory';
import Recipes from './pages/Recipes';
import FinancialAnalytics from './pages/FinancialAnalytics';
import OperationalAnalytics from './pages/OperationalAnalytics';
import CustomerAnalytics from './pages/CustomerAnalytics';
import InventoryAnalytics from './pages/InventoryAnalytics';
import PurchaseOrders from './pages/PurchaseOrders';
import Expenses from './pages/Expenses';
import FinancialReports from './pages/FinancialReports';
import POSApp from './pos/POSApp';
import KitchenTicketPage from './pos/KitchenTicketPage';

function PrivateRoute({ children }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return isAuthenticated ? children : <Navigate to="/login" />;
}

function App() {
  return (
    <BrowserRouter>
      <SessionManager />
      <Routes>
        <Route path="/login" element={<Login />} />

        {/* POS System Routes - No authentication required */}
        <Route path="/pos" element={<POSApp />} />
        <Route path="/pos/kitchen-ticket" element={<KitchenTicketPage />} />

        <Route
          path="/"
          element={
            <PrivateRoute>
              <Layout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="dashboard/financial-analytics" element={<FinancialAnalytics />} />
          <Route path="dashboard/operational-analytics" element={<OperationalAnalytics />} />
          <Route path="dashboard/customer-analytics" element={<CustomerAnalytics />} />
          <Route path="dashboard/inventory-analytics" element={<InventoryAnalytics />} />
          <Route path="orders" element={<Orders />} />
          <Route path="restaurants" element={<Restaurants />} />
          <Route path="products" element={<Products />} />
          <Route path="products/:productId/linked-items" element={<LinkedItems />} />
          <Route path="menu" element={<Menu />} />
          <Route path="catalog/categories" element={<Categories />} />
          <Route path="menu-collections" element={<MenuCollections />} />
          <Route path="customers" element={<Customers />} />
          <Route path="customer-segments" element={<CustomerSegments />} />
          <Route path="operators" element={<Operators />} />
          <Route path="couriers" element={<Couriers />} />
          <Route path="courier-map" element={<CourierMap />} />
          <Route path="kitchen" element={<KitchenDashboard />} />
          <Route path="kitchen/inventory" element={<Inventory />} />
          <Route path="kitchen/recipes" element={<Recipes />} />
          <Route path="finance/purchase-orders" element={<PurchaseOrders />} />
          <Route path="finance/expenses" element={<Expenses />} />
          <Route path="finance/reports" element={<FinancialReports />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
