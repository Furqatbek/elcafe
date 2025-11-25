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
import Customers from './pages/Customers';
import CustomerSegments from './pages/CustomerSegments';
import Operators from './pages/Operators';
import Couriers from './pages/Couriers';
import CourierMap from './pages/CourierMap';
import MenuCollections from './pages/MenuCollections';
import KitchenDashboard from './pages/KitchenDashboard';
import FinancialAnalytics from './pages/FinancialAnalytics';
import OperationalAnalytics from './pages/OperationalAnalytics';
import CustomerAnalytics from './pages/CustomerAnalytics';
import InventoryAnalytics from './pages/InventoryAnalytics';

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
          <Route path="menu-collections" element={<MenuCollections />} />
          <Route path="customers" element={<Customers />} />
          <Route path="customer-segments" element={<CustomerSegments />} />
          <Route path="operators" element={<Operators />} />
          <Route path="couriers" element={<Couriers />} />
          <Route path="courier-map" element={<CourierMap />} />
          <Route path="kitchen" element={<KitchenDashboard />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
