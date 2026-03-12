import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import Layout from './components/Layout';
import SessionManager from './components/SessionManager';
import { Toaster } from './components/ui/toaster';
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
import WaiterPerformance from './pages/WaiterPerformance';
import Couriers from './pages/Couriers';
import CourierMap from './pages/CourierMap';
import MenuCollections from './pages/MenuCollections';
import KitchenDashboard from './pages/KitchenDashboard';
import FinancialAnalytics from './pages/FinancialAnalytics';
import OperationalAnalytics from './pages/OperationalAnalytics';
import CustomerAnalytics from './pages/CustomerAnalytics';
import InventoryAnalytics from './pages/InventoryAnalytics';
import PromotionAnalytics from './pages/PromotionAnalytics';
import PurchaseOrders from './pages/PurchaseOrders';
import POSuggestions from './pages/POSuggestions';
import Expenses from './pages/Expenses';
import FinancialReports from './pages/FinancialReports';
import PrinterSettings from './pages/PrinterSettings';
import KitchenStations from './pages/KitchenStations';
import PricingDashboard from './pages/PricingDashboard';
import FinancialAlerts from './pages/FinancialAlerts';
import Promotions from './pages/Promotions';
import CouponCodes from './pages/CouponCodes';
import HappyHours from './pages/HappyHours';
import Bundles from './pages/Bundles';
import ReferralProgram from './pages/ReferralProgram';
import SmsMarketing from './pages/SmsMarketing';
import TelegramMarketing from './pages/TelegramMarketing';
import QRCodes from './pages/QRCodes';
import LoyaltyMilestones from './pages/LoyaltyMilestones';
import Reservations from './pages/Reservations';
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
import OrdersHistory from './pages/OrdersHistory';
import SelfServiceOrders from './pages/SelfServiceOrders';

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
  // Set default restaurantId on app load if not already set
  useEffect(() => {
    if (!localStorage.getItem('selectedRestaurantId')) {
      localStorage.setItem('selectedRestaurantId', '1');
    }
  }, []);

  return (
    <BrowserRouter basename="/admin">
      <SessionManager />
      <Toaster />
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
          <Route path="orders/history" element={<OrdersHistory />} />
          <Route path="orders/self-service" element={<SelfServiceOrders />} />
          <Route path="restaurants" element={<Restaurants />} />
          <Route path="restaurants/tables" element={<Tables />} />
          <Route path="restaurants/working-hours" element={<WorkingHours />} />
          <Route path="restaurants/reservations" element={<AdminRoute><Reservations /></AdminRoute>} />
          <Route path="products" element={<Products />} />
          <Route path="products/:productId/linked-items" element={<LinkedItems />} />
          <Route path="menu" element={<Menu />} />
          <Route path="catalog/categories" element={<Categories />} />
          <Route path="menu-collections" element={<MenuCollections />} />
          <Route path="customers" element={<Customers />} />
          <Route path="customer-segments" element={<CustomerSegments />} />
          <Route path="operators" element={<Operators />} />
          <Route path="employees/waiters" element={<Waiters />} />
          <Route path="employees/waiter-performance" element={<AdminRoute><WaiterPerformance /></AdminRoute>} />
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
          <Route path="marketing/bundles" element={<AdminRoute><Bundles /></AdminRoute>} />
          <Route path="marketing/referrals" element={<AdminRoute><ReferralProgram /></AdminRoute>} />
          <Route path="marketing/sms" element={<AdminRoute><SmsMarketing /></AdminRoute>} />
          <Route path="marketing/telegram" element={<AdminRoute><TelegramMarketing /></AdminRoute>} />
          <Route path="marketing/qr-codes" element={<AdminRoute><QRCodes /></AdminRoute>} />
          <Route path="marketing/milestones" element={<AdminRoute><LoyaltyMilestones /></AdminRoute>} />
          <Route path="marketing/analytics" element={<AdminRoute><PromotionAnalytics /></AdminRoute>} />
          <Route path="promotions" element={<AdminRoute><Promotions /></AdminRoute>} />
          <Route path="coupons" element={<AdminRoute><CouponCodes /></AdminRoute>} />
          <Route path="happy-hours" element={<AdminRoute><HappyHours /></AdminRoute>} />
          <Route path="bundles" element={<AdminRoute><Bundles /></AdminRoute>} />
          <Route path="referrals" element={<AdminRoute><ReferralProgram /></AdminRoute>} />
          <Route path="settings/printers" element={<PrinterSettings />} />
          <Route path="settings/kitchen-stations" element={<AdminRoute><KitchenStations /></AdminRoute>} />
          <Route path="pos" element={<POSApp />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
