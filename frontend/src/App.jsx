import { useEffect, Suspense } from 'react';
import { lazyWithRetry as lazy } from './lib/lazyWithRetry';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/authStore';
import Layout from './components/Layout';
import SessionManager from './components/SessionManager';
import SuspensionGate from './components/SuspensionGate';
import { Toaster } from './components/ui/toaster';
import { InventoryProvider } from './context/InventoryContext';
import { receiptTemplateAPI } from './services/api';
import { resolveCurrentRestaurantId } from './utils/restaurant';

// Route pages are lazy-loaded so the initial bundle stays small; each becomes its own chunk
// fetched on navigation (rendered under the <Suspense> boundary below).
const Login = lazy(() => import('./pages/Login'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Orders = lazy(() => import('./pages/Orders'));
const Restaurants = lazy(() => import('./pages/Restaurants'));
const Tables = lazy(() => import('./pages/Tables'));
const FloorPlan = lazy(() => import('./pages/FloorPlan'));
const WorkingHours = lazy(() => import('./pages/WorkingHours'));
const Products = lazy(() => import('./pages/Products'));
const LinkedItems = lazy(() => import('./pages/LinkedItems'));
const Menu = lazy(() => import('./pages/Menu'));
const Categories = lazy(() => import('./pages/Categories'));
const Customers = lazy(() => import('./pages/Customers'));
const CustomerProfile = lazy(() => import('./pages/CustomerProfile'));
const Reviews = lazy(() => import('./pages/Reviews'));
const CustomerSegments = lazy(() => import('./pages/CustomerSegments'));
const Operators = lazy(() => import('./pages/Operators'));
const SystemUsers = lazy(() => import('./pages/SystemUsers'));
const Subscription = lazy(() => import('./pages/Subscription'));
const PlatformConsole = lazy(() => import('./pages/PlatformConsole'));
const Waiters = lazy(() => import('./pages/Waiters'));
const WaiterPerformance = lazy(() => import('./pages/WaiterPerformance'));
const ShiftDashboard = lazy(() => import('./pages/ShiftDashboard'));
const ShiftSchedule = lazy(() => import('./pages/ShiftSchedule'));
const MobileClockIn = lazy(() => import('./pages/MobileClockIn'));
const Couriers = lazy(() => import('./pages/Couriers'));
const CourierMap = lazy(() => import('./pages/CourierMap'));
const MenuCollections = lazy(() => import('./pages/MenuCollections'));
const KitchenDashboard = lazy(() => import('./pages/KitchenDashboard'));
const FinancialAnalytics = lazy(() => import('./pages/FinancialAnalytics'));
const OperationalAnalytics = lazy(() => import('./pages/OperationalAnalytics'));
const CustomerAnalytics = lazy(() => import('./pages/CustomerAnalytics'));
const InventoryAnalytics = lazy(() => import('./pages/InventoryAnalytics'));
const PromotionAnalytics = lazy(() => import('./pages/PromotionAnalytics'));
const PurchaseOrders = lazy(() => import('./pages/PurchaseOrders'));
const POSuggestions = lazy(() => import('./pages/POSuggestions'));
const Expenses = lazy(() => import('./pages/Expenses'));
const FinancialReports = lazy(() => import('./pages/FinancialReports'));
const PrinterSettings = lazy(() => import('./pages/PrinterSettings'));
const ReceiptTemplateSettings = lazy(() => import('./pages/ReceiptTemplateSettings'));
const KitchenStations = lazy(() => import('./pages/KitchenStations'));
const PricingDashboard = lazy(() => import('./pages/PricingDashboard'));
const FinancialAlerts = lazy(() => import('./pages/FinancialAlerts'));
const Payroll = lazy(() => import('./pages/Payroll'));
const EmployeeConsumption = lazy(() => import('./pages/EmployeeConsumption'));
const ConsumptionAllowances = lazy(() => import('./pages/ConsumptionAllowances'));
const Promotions = lazy(() => import('./pages/Promotions'));
const CouponCodes = lazy(() => import('./pages/CouponCodes'));
const HappyHours = lazy(() => import('./pages/HappyHours'));
const Bundles = lazy(() => import('./pages/Bundles'));
const ReferralProgram = lazy(() => import('./pages/ReferralProgram'));
const SmsMarketing = lazy(() => import('./pages/SmsMarketing'));
const TelegramMarketing = lazy(() => import('./pages/TelegramMarketing'));
const OwnerBotSubscribers = lazy(() => import('./pages/OwnerBotSubscribers'));
const InstagramMarketing = lazy(() => import('./pages/InstagramMarketing'));
const QRCodes = lazy(() => import('./pages/QRCodes'));
const LoyaltyMilestones = lazy(() => import('./pages/LoyaltyMilestones'));
const LoyaltySettings = lazy(() => import('./pages/LoyaltySettings'));
const Reservations = lazy(() => import('./pages/Reservations'));
const POSApp = lazy(() => import('./pos/POSApp'));
const SinglePagePOS = lazy(() => import('./pos/single/SinglePagePOS'));
const CustomerDisplayScreen = lazy(() => import('./pos/screens/CustomerDisplayScreen'));
const OrderStatusBoardScreen = lazy(() => import('./pos/screens/OrderStatusBoardScreen'));
const ReviewPage = lazy(() => import('./pages/customer/ReviewPage'));
const OrdersHistory = lazy(() => import('./pages/OrdersHistory'));
const OrdersByShift = lazy(() => import('./pages/OrdersByShift'));
const SelfServiceOrders = lazy(() => import('./pages/SelfServiceOrders'));
const Profile = lazy(() => import('./pages/Profile'));
// Inventory pages are named exports of ./pages/inventory — map each to a default for lazy().
const InventoryIngredients = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventoryIngredients })));
const InventoryRecipes = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventoryRecipes })));
const InventoryExpiry = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventoryExpiry })));
const InventoryStockCounts = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventoryStockCounts })));
const InventoryWaste = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventoryWaste })));
const InventorySuppliers = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventorySuppliers })));
const InventoryAlerts = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventoryAlerts })));
const InventoryValuation = lazy(() => import('./pages/inventory').then((m) => ({ default: m.InventoryValuation })));
const ProductionBatches = lazy(() => import('./pages/inventory').then((m) => ({ default: m.ProductionBatches })));

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

// Platform console is cross-tenant — only the SUPER_ADMIN platform operator may reach it.
function SuperAdminRoute({ children }) {
  const user = useAuthStore((state) => state.user);
  if (user?.role !== 'SUPER_ADMIN') {
    return <Navigate to="/orders" replace />;
  }
  return children;
}

function InventoryWrapper({ children }) {
  return <InventoryProvider>{children}</InventoryProvider>;
}

function App() {
  // Load receipt template once on startup so PrintReceipt can use it.
  // The restaurant id is resolved via the auth user → localStorage →
  // /restaurants/active fallback chain; if none of them yield an id we
  // skip the call rather than guessing restaurant 1.
  useEffect(() => {
    // Plan awareness (mini-phase A3): load the caller's subscription plan once on startup.
    if (useAuthStore.getState().isAuthenticated) {
      useAuthStore.getState().loadPlan();
    }
    resolveCurrentRestaurantId().then((restaurantId) => {
      if (!restaurantId) return;
      receiptTemplateAPI.getTemplate(restaurantId)
        .then(res => {
          if (res.data?.data) {
            localStorage.setItem('receiptTemplate', JSON.stringify(res.data.data));
          }
        })
        .catch(() => {}); // Silently ignore — PrintReceipt falls back to built-in defaults
    });
  }, []);

  return (
    <BrowserRouter basename="/admin">
      <SessionManager />
      <Toaster />
      <SuspensionGate />
      <Suspense fallback={<div className="flex h-screen items-center justify-center text-gray-500">Loading…</div>}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/pos/customer-display" element={<CustomerDisplayScreen />} />
        <Route path="/pos/order-status" element={<OrderStatusBoardScreen />} />
        <Route
          path="/pos"
          element={
            <PrivateRoute>
              <SinglePagePOS />
            </PrivateRoute>
          }
        />
        <Route
          path="/pos/legacy"
          element={
            <PrivateRoute>
              <POSApp />
            </PrivateRoute>
          }
        />
        <Route path="/review" element={<ReviewPage />} />
        <Route path="/shift/clock" element={<MobileClockIn />} />
        <Route
          path="/"
          element={
            <PrivateRoute>
              <Layout />
            </PrivateRoute>
          }
        >
          <Route index element={<Navigate to="/orders" replace />} />
          <Route path="subscription" element={<Subscription />} />
          <Route path="platform" element={<SuperAdminRoute><PlatformConsole /></SuperAdminRoute>} />
          <Route path="dashboard" element={<AdminRoute><Dashboard /></AdminRoute>} />
          <Route path="dashboard/financial-analytics" element={<AdminRoute><FinancialAnalytics /></AdminRoute>} />
          <Route path="dashboard/operational-analytics" element={<AdminRoute><OperationalAnalytics /></AdminRoute>} />
          <Route path="dashboard/customer-analytics" element={<AdminRoute><CustomerAnalytics /></AdminRoute>} />
          <Route path="dashboard/inventory-analytics" element={<AdminRoute><InventoryAnalytics /></AdminRoute>} />
          <Route path="orders" element={<Orders />} />
          <Route path="orders/history" element={<OrdersHistory />} />
          <Route path="orders/by-shift" element={<OrdersByShift />} />
          <Route path="orders/self-service" element={<SelfServiceOrders />} />
          <Route path="restaurants" element={<Restaurants />} />
          <Route path="restaurants/tables" element={<Tables />} />
          {/* Not wrapped in AdminRoute: reading the live room is open to the restaurant's staff, and
              the layout writes carry their own owner/manager gate on the server. */}
          <Route path="restaurants/floor-map" element={<FloorPlan />} />
          <Route path="restaurants/working-hours" element={<WorkingHours />} />
          <Route path="restaurants/reservations" element={<AdminRoute><Reservations /></AdminRoute>} />
          <Route path="products" element={<Products />} />
          <Route path="products/:productId/linked-items" element={<LinkedItems />} />
          <Route path="menu" element={<Menu />} />
          <Route path="catalog/categories" element={<Categories />} />
          <Route path="menu-collections" element={<MenuCollections />} />
          <Route path="customers" element={<Customers />} />
          <Route path="customers/:customerId" element={<CustomerProfile />} />
          <Route path="customer-segments" element={<CustomerSegments />} />
          <Route path="reviews" element={<Reviews />} />
          <Route path="operators" element={<Operators />} />
          <Route path="system-users" element={<AdminRoute><SystemUsers /></AdminRoute>} />
          <Route path="employees/waiters" element={<Waiters />} />
          <Route path="employees/waiter-performance" element={<AdminRoute><WaiterPerformance /></AdminRoute>} />
          <Route path="employees/shift-dashboard" element={<ShiftDashboard />} />
          <Route path="employees/consumption" element={<EmployeeConsumption />} />
          <Route path="employees/consumption-allowances" element={<AdminRoute><ConsumptionAllowances /></AdminRoute>} />
          <Route path="employees/shift-schedule" element={<ShiftSchedule />} />
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
          <Route path="kitchen/production" element={<InventoryWrapper><ProductionBatches /></InventoryWrapper>} />
          <Route path="kitchen/po-suggestions" element={<POSuggestions />} />
          <Route path="finance/purchase-orders" element={<AdminRoute><PurchaseOrders /></AdminRoute>} />
          <Route path="finance/expenses" element={<AdminRoute><Expenses /></AdminRoute>} />
          <Route path="finance/reports" element={<AdminRoute><FinancialReports /></AdminRoute>} />
          <Route path="finance/pricing" element={<AdminRoute><PricingDashboard /></AdminRoute>} />
          <Route path="finance/alerts" element={<AdminRoute><FinancialAlerts /></AdminRoute>} />
          <Route path="finance/payroll" element={<AdminRoute><Payroll /></AdminRoute>} />
          <Route path="marketing/promotions" element={<AdminRoute><Promotions /></AdminRoute>} />
          <Route path="marketing/coupons" element={<AdminRoute><CouponCodes /></AdminRoute>} />
          <Route path="marketing/happy-hours" element={<AdminRoute><HappyHours /></AdminRoute>} />
          <Route path="marketing/bundles" element={<AdminRoute><Bundles /></AdminRoute>} />
          <Route path="marketing/referrals" element={<AdminRoute><ReferralProgram /></AdminRoute>} />
          <Route path="marketing/sms" element={<AdminRoute><SmsMarketing /></AdminRoute>} />
          <Route path="marketing/telegram" element={<AdminRoute><TelegramMarketing /></AdminRoute>} />
          <Route path="settings/telegram-subscribers" element={<AdminRoute><OwnerBotSubscribers /></AdminRoute>} />
          <Route path="marketing/instagram" element={<AdminRoute><InstagramMarketing /></AdminRoute>} />
          <Route path="marketing/qr-codes" element={<AdminRoute><QRCodes /></AdminRoute>} />
          <Route path="marketing/milestones" element={<AdminRoute><LoyaltyMilestones /></AdminRoute>} />
          <Route path="marketing/loyalty" element={<AdminRoute><LoyaltySettings /></AdminRoute>} />
          <Route path="marketing/analytics" element={<AdminRoute><PromotionAnalytics /></AdminRoute>} />
          <Route path="promotions" element={<AdminRoute><Promotions /></AdminRoute>} />
          <Route path="coupons" element={<AdminRoute><CouponCodes /></AdminRoute>} />
          <Route path="happy-hours" element={<AdminRoute><HappyHours /></AdminRoute>} />
          <Route path="bundles" element={<AdminRoute><Bundles /></AdminRoute>} />
          <Route path="referrals" element={<AdminRoute><ReferralProgram /></AdminRoute>} />
          <Route path="settings/printers" element={<PrinterSettings />} />
          <Route path="settings/receipt-template" element={<AdminRoute><ReceiptTemplateSettings /></AdminRoute>} />
          <Route path="settings/kitchen-stations" element={<AdminRoute><KitchenStations /></AdminRoute>} />
          <Route path="profile" element={<Profile />} />
          <Route path="*" element={<Navigate to="/orders" replace />} />
        </Route>
      </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;
