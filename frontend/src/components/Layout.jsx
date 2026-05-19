import { useState } from 'react';
import { Link, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { useNotificationStore } from '../store/notificationStore';
import { Button } from './ui/button';
import { LanguageSwitcher } from './LanguageSwitcher';
import PushPermissionPrompt from './PushPermissionPrompt';
import NotificationBell from './OrderNotificationProvider';
import {
  LayoutDashboard,
  ShoppingCart,
  Users,
  UserCog,
  BookOpen,
  Megaphone,
  ChefHat,
  Settings,
  UserCircle,
  ChevronDown,
  ChevronRight,
  LogOut,
  BarChart3,
  TrendingUp,
  Package,
  Clock,
  UserCheck,
  History,
  Calendar,
  Grid,
  List,
  Tag,
  Target,
  Bell,
  Utensils,
  Soup,
  Cookie,
  Truck,
  MapPin,
  Store,
  Table,
  Printer,
  Wallet,
  FileText,
  Receipt,
  AlertTriangle,
  ClipboardCheck,
  Trash2,
  Monitor,
  Calculator,
  Ticket,
  Wine,
  Share2,
  MessageSquare,
  Send,
  Bot,
  QrCode,
  Trophy,
  Stamp,
  Globe,
  Instagram,
  Star,
} from 'lucide-react';

export default function Layout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useAuthStore();
  const { unreadReservations } = useNotificationStore();
  const [expandedMenus, setExpandedMenus] = useState({});

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const toggleMenu = (menuId) => {
    setExpandedMenus((prev) => ({
      ...prev,
      [menuId]: !prev[menuId],
    }));
  };

  const isActive = (path) => location.pathname === path;

  const menuItems = [
    {
      id: 'dashboard',
      label: t('nav.dashboard'),
      icon: LayoutDashboard,
      path: '/dashboard',
      subItems: [
        { label: t('nav.sub.financialAnalytics'), icon: TrendingUp, path: '/dashboard/financial-analytics' },
        { label: t('nav.sub.operationalAnalytics'), icon: BarChart3, path: '/dashboard/operational-analytics' },
        { label: t('nav.sub.customerAnalytics'), icon: Users, path: '/dashboard/customer-analytics' },
        { label: t('nav.sub.inventoryAnalytics'), icon: Package, path: '/dashboard/inventory-analytics' },
      ],
    },
    {
      id: 'pos',
      label: t('nav.pos'),
      icon: Monitor,
      path: '/pos',
      subItems: [],
    },
    {
      id: 'orders',
      label: t('nav.orders'),
      icon: ShoppingCart,
      path: '/orders',
      subItems: [
        { label: t('nav.sub.ordersHistory'), icon: History, path: '/orders/history' },
        { label: t('nav.sub.externalOrders', 'Online Orders'), icon: Globe, path: '/orders/self-service' },
      ],
    },
    {
      id: 'restaurant',
      label: t('nav.restaurant'),
      icon: Store,
      path: '/restaurants/tables',
      badge: unreadReservations,
      subItems: [
        { label: t('nav.sub.branches'), icon: Grid, path: '/restaurants' },
        { label: t('nav.sub.tables'), icon: Table, path: '/restaurants/tables' },
        { label: t('nav.sub.workingHours'), icon: Clock, path: '/restaurants/working-hours' },
        { label: t('nav.sub.reservations', 'Reservations'), icon: Calendar, path: '/restaurants/reservations', badge: unreadReservations },
      ],
    },
    {
      id: 'clients',
      label: t('nav.clients'),
      icon: Users,
      path: '/customers',
      subItems: [
        { label: t('nav.sub.customers'), icon: UserCheck, path: '/customers' },
        { label: t('nav.sub.customerSegments'), icon: Target, path: '/customer-segments' },
      ],
    },
    {
      id: 'employees',
      label: t('nav.employees'),
      icon: UserCog,
      path: '/operators',
      subItems: [
        { label: t('nav.sub.operators'), icon: UserCog, path: '/operators' },
        { label: t('nav.sub.waiters'), icon: UserCheck, path: '/employees/waiters' },
        { label: t('nav.sub.waiterPerformance', 'Waiter Performance'), icon: Trophy, path: '/employees/waiter-performance' },
        { label: t('nav.sub.shiftDashboard', 'Shift Dashboard'), icon: Clock, path: '/employees/shift-dashboard' },
        { label: t('nav.sub.shiftSchedule', 'Shift Schedule'), icon: Calendar, path: '/employees/shift-schedule' },
        { label: t('nav.sub.consumption', 'Consumption'), icon: Utensils, path: '/employees/consumption' },
        { label: t('nav.sub.consumptionAllowances', 'Consumption allowances'), icon: Utensils, path: '/employees/consumption-allowances' },
        { label: t('nav.sub.couriers'), icon: Truck, path: '/couriers' },
        { label: t('nav.sub.courierMap'), icon: MapPin, path: '/courier-map' },
      ],
    },
    {
      id: 'catalog',
      label: t('nav.catalog'),
      icon: BookOpen,
      path: '/products',
      subItems: [
        { label: t('nav.sub.products'), icon: Package, path: '/products' },
        { label: t('nav.sub.menu'), icon: Utensils, path: '/menu' },
        { label: t('nav.sub.categories'), icon: List, path: '/catalog/categories' },
        { label: t('nav.sub.menuCollections'), icon: Package, path: '/menu-collections' },
      ],
    },
    {
      id: 'kitchen',
      label: t('nav.kitchen'),
      icon: ChefHat,
      path: '/kitchen',
      subItems: [
        { label: t('nav.sub.kitchenDashboard'), icon: Utensils, path: '/kitchen' },
        { label: t('nav.sub.inventory'), icon: Cookie, path: '/kitchen/inventory' },
        { label: t('nav.sub.recipes'), icon: Soup, path: '/kitchen/recipes' },
        { label: t('nav.sub.expiry'), icon: Calendar, path: '/kitchen/expiry' },
        { label: t('nav.sub.stockCounts'), icon: ClipboardCheck, path: '/kitchen/stock-counts' },
        { label: t('nav.sub.waste'), icon: Trash2, path: '/kitchen/waste' },
        { label: t('nav.sub.suppliers'), icon: Truck, path: '/kitchen/suppliers' },
        { label: t('nav.sub.stockAlerts'), icon: AlertTriangle, path: '/kitchen/stock-alerts' },
        { label: t('nav.sub.valuation'), icon: Calculator, path: '/kitchen/valuation' },
        { label: t('nav.sub.poSuggestions'), icon: ShoppingCart, path: '/kitchen/po-suggestions' },
        { label: t('nav.sub.production', 'Production'), icon: ChefHat, path: '/kitchen/production' },
      ],
    },
    {
      id: 'marketing',
      label: t('nav.marketing'),
      icon: Megaphone,
      path: '/marketing/promotions',
      subItems: [
        { label: t('nav.sub.promotions', 'Promotions'), icon: Tag, path: '/marketing/promotions' },
        { label: t('nav.sub.coupons', 'Coupons'), icon: Ticket, path: '/marketing/coupons' },
        { label: t('nav.sub.happyHours', 'Happy Hours'), icon: Wine, path: '/marketing/happy-hours' },
        { label: t('nav.sub.bundles', 'Bundles'), icon: Package, path: '/marketing/bundles' },
        { label: t('nav.sub.referrals', 'Referrals'), icon: Share2, path: '/marketing/referrals' },
        { label: t('nav.sub.smsMarketing', 'SMS Marketing'), icon: MessageSquare, path: '/marketing/sms' },
        { label: t('nav.sub.telegramMarketing', 'Telegram'), icon: Send, path: '/marketing/telegram' },
        { label: t('nav.sub.telegramSubscribers', 'Telegram subscribers'), icon: Bot, path: '/settings/telegram-subscribers' },
        { label: t('nav.sub.instagramMarketing', 'Instagram'), icon: Instagram, path: '/marketing/instagram' },
        { label: t('nav.sub.qrCodes', 'QR Codes'), icon: QrCode, path: '/marketing/qr-codes' },
        { label: t('nav.sub.milestones', 'Milestones'), icon: Stamp, path: '/marketing/milestones' },
        { label: t('nav.sub.promotionAnalytics', 'Analytics'), icon: BarChart3, path: '/marketing/analytics' },
        { label: t('nav.sub.reviews', 'Reviews'), icon: Star, path: '/reviews' },
      ],
    },
    {
      id: 'finance',
      label: t('nav.finance'),
      icon: Wallet,
      path: '/finance/purchase-orders',
      subItems: [
        { label: t('nav.sub.purchaseOrders'), icon: FileText, path: '/finance/purchase-orders' },
        { label: t('nav.sub.expenses'), icon: Receipt, path: '/finance/expenses' },
        { label: t('nav.sub.financialReports'), icon: BarChart3, path: '/finance/reports' },
        { label: t('nav.sub.pricing'), icon: Calculator, path: '/finance/pricing' },
        { label: t('nav.sub.financialAlerts'), icon: Bell, path: '/finance/alerts' },
        { label: t('nav.sub.payroll'), icon: Users, path: '/finance/payroll' },
      ],
    },
    {
      id: 'settings',
      label: t('nav.settings'),
      icon: Settings,
      path: '/settings/printers',
      subItems: [
        { label: t('nav.sub.systemUsers', 'System Users'), icon: UserCog, path: '/system-users' },
        { label: t('nav.sub.printers'), icon: Printer, path: '/settings/printers' },
        { label: t('nav.sub.receiptTemplate', 'Chek Shabloni'), icon: Receipt, path: '/settings/receipt-template' },
        { label: t('nav.sub.kitchenStations', 'Kitchen Stations'), icon: ChefHat, path: '/settings/kitchen-stations' },
        { label: t('nav.sub.telegramSubscribers', 'Telegram subscribers'), icon: Send, path: '/settings/telegram-subscribers' },
      ],
    },
  ];

  // Filter menu items based on user role
  // OPERATOR role cannot access dashboard and finance
  const isOperator = user?.role === 'OPERATOR';
  const filteredMenuItems = isOperator
    ? menuItems.filter((item) => !['dashboard', 'finance', 'marketing'].includes(item.id))
    : menuItems;

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
        {/* Profile Section */}
        <div className="p-4 border-b border-gray-200">
          <div className="flex items-center space-x-3 mb-4">
            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white font-semibold">
              {user?.firstName?.[0] || user?.email?.[0]?.toUpperCase() || 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-gray-900 truncate">
                {user?.firstName || user?.email || 'User'}
              </p>
              <p className="text-xs text-gray-500 truncate">{user?.role || 'Admin'}</p>
            </div>
            {/* Notification Bell */}
            <NotificationBell />
          </div>
          <LanguageSwitcher />
        </div>

        {/* Navigation Menu */}
        <nav className="flex-1 overflow-y-auto py-4">
          <ul className="space-y-1 px-2">
            {filteredMenuItems.map((item) => (
              <li key={item.id}>
                <div>
                  {item.subItems.length === 0 ? (
                    // Direct link for items without sub-items.
                    // POS opens in a new tab so the cashier gets the full screen
                    // without admin sidebars.
                    item.id === 'pos' ? (
                      <a
                        href={`/admin${item.path}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="w-full flex items-center space-x-3 px-3 py-2 text-sm font-medium rounded-lg transition-colors text-gray-700 hover:bg-gray-100"
                      >
                        <item.icon className="h-5 w-5" />
                        <span>{item.label}</span>
                      </a>
                    ) : (
                      <Link
                        to={item.path}
                        className={`w-full flex items-center space-x-3 px-3 py-2 text-sm font-medium rounded-lg transition-colors ${
                          isActive(item.path)
                            ? 'bg-blue-50 text-blue-700'
                            : 'text-gray-700 hover:bg-gray-100'
                        }`}
                      >
                        <item.icon className="h-5 w-5" />
                        <span>{item.label}</span>
                      </Link>
                    )
                  ) : (
                    // Expandable menu for items with sub-items
                    <>
                      <button
                        onClick={() => {
                          toggleMenu(item.id);
                          if (!expandedMenus[item.id]) {
                            navigate(item.path);
                          }
                        }}
                        className={`w-full flex items-center justify-between px-3 py-2 text-sm font-medium rounded-lg transition-colors ${
                          isActive(item.path)
                            ? 'bg-blue-50 text-blue-700'
                            : 'text-gray-700 hover:bg-gray-100'
                        }`}
                      >
                        <div className="flex items-center space-x-3">
                          <div className="relative">
                            <item.icon className="h-5 w-5" />
                            {item.badge > 0 && !expandedMenus[item.id] && (
                              <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs font-bold w-4 h-4 rounded-full flex items-center justify-center animate-pulse">
                                {item.badge > 9 ? '!' : item.badge}
                              </span>
                            )}
                          </div>
                          <span>{item.label}</span>
                        </div>
                        <div className="flex items-center space-x-2">
                          {item.badge > 0 && expandedMenus[item.id] && (
                            <span className="bg-red-500 text-white text-xs font-bold px-1.5 py-0.5 rounded-full">
                              {item.badge > 99 ? '99+' : item.badge}
                            </span>
                          )}
                          {expandedMenus[item.id] ? (
                            <ChevronDown className="h-4 w-4" />
                          ) : (
                            <ChevronRight className="h-4 w-4" />
                          )}
                        </div>
                      </button>

                      {/* Sub-menu */}
                      {expandedMenus[item.id] && (
                        <ul className="mt-1 ml-4 space-y-1">
                          {item.subItems.map((subItem, idx) => (
                            <li key={idx}>
                              <Link
                                to={subItem.path}
                                className={`flex items-center justify-between px-3 py-2 text-sm rounded-lg transition-colors ${
                                  isActive(subItem.path)
                                    ? 'bg-blue-50 text-blue-700'
                                    : 'text-gray-600 hover:bg-gray-100'
                                }`}
                              >
                                <div className="flex items-center space-x-3">
                                  <subItem.icon className="h-4 w-4" />
                                  <span>{subItem.label}</span>
                                </div>
                                {subItem.badge > 0 && (
                                  <span className="ml-2 bg-red-500 text-white text-xs font-bold px-2 py-0.5 rounded-full min-w-[20px] text-center animate-pulse">
                                    {subItem.badge > 99 ? '99+' : subItem.badge}
                                  </span>
                                )}
                              </Link>
                            </li>
                          ))}
                        </ul>
                      )}
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </nav>

        {/* Account Profile & Logout */}
        <div className="p-4 border-t border-gray-200 space-y-2">
          <Link
            to="/profile"
            className={`flex items-center space-x-3 px-3 py-2 text-sm font-medium rounded-lg transition-colors ${
              isActive('/profile')
                ? 'bg-blue-50 text-blue-700'
                : 'text-gray-700 hover:bg-gray-100'
            }`}
          >
            <UserCircle className="h-5 w-5" />
            <span>{t('nav.accountProfile')}</span>
          </Link>
          <Button
            variant="ghost"
            className="w-full justify-start text-red-600 hover:text-red-700 hover:bg-red-50"
            onClick={handleLogout}
          >
            <LogOut className="h-5 w-5 mr-3" />
            {t('auth.logout')}
          </Button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 overflow-auto">
        <div className="p-8">
          <Outlet />
        </div>
      </main>

      {/* Push Notification Permission Prompt */}
      <PushPermissionPrompt isAdmin={true} />
    </div>
  );
}
