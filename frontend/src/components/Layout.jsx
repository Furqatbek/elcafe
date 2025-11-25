import { useState } from 'react';
import { Link, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { Button } from './ui/button';
import { LanguageSwitcher } from './LanguageSwitcher';
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
  PieChart,
  Package,
  Clock,
  CheckCircle,
  UserPlus,
  UserCheck,
  History,
  Briefcase,
  Calendar,
  Award,
  Grid,
  List,
  Tag,
  Target,
  Mail,
  Bell,
  Utensils,
  Soup,
  Cookie,
  Wrench,
  Shield,
  CreditCard,
  Truck,
  MapPin,
} from 'lucide-react';

export default function Layout() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useAuthStore();
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
      id: 'orders',
      label: t('nav.orders'),
      icon: ShoppingCart,
      path: '/orders',
      subItems: [
        { label: t('nav.sub.allOrders'), icon: Package, path: '/orders' },
        { label: t('nav.sub.pending'), icon: Clock, path: '/orders/pending' },
        { label: t('nav.sub.completed'), icon: CheckCircle, path: '/orders/completed' },
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
        { label: t('nav.sub.couriers'), icon: Truck, path: '/couriers' },
        { label: t('nav.sub.courierMap'), icon: MapPin, path: '/courier-map' },
        { label: t('nav.sub.schedule'), icon: Calendar, path: '/employees/schedule' },
        { label: t('nav.sub.performance'), icon: Award, path: '/employees/performance' },
      ],
    },
    {
      id: 'catalog',
      label: t('nav.catalog'),
      icon: BookOpen,
      path: '/restaurants',
      subItems: [
        { label: t('nav.sub.branches'), icon: Grid, path: '/restaurants' },
        { label: t('nav.sub.products'), icon: Package, path: '/products' },
        { label: t('nav.sub.categories'), icon: List, path: '/catalog/categories' },
        { label: t('nav.sub.menuCollections'), icon: Package, path: '/menu-collections' },
        { label: t('nav.sub.pricing'), icon: Tag, path: '/catalog/pricing' },
      ],
    },
    {
      id: 'marketing',
      label: t('nav.marketing'),
      icon: Megaphone,
      path: '/marketing',
      subItems: [
        { label: t('nav.sub.campaigns'), icon: Target, path: '/marketing/campaigns' },
        { label: t('nav.sub.emails'), icon: Mail, path: '/marketing/emails' },
        { label: t('nav.sub.notifications'), icon: Bell, path: '/marketing/notifications' },
      ],
    },
    {
      id: 'kitchen',
      label: t('nav.kitchen'),
      icon: ChefHat,
      path: '/kitchen',
      subItems: [
        { label: t('nav.sub.kitchenDashboard'), icon: Utensils, path: '/kitchen' },
        { label: t('nav.sub.recipes'), icon: Soup, path: '/kitchen/recipes' },
        { label: t('nav.sub.inventory'), icon: Cookie, path: '/kitchen/inventory' },
      ],
    },
    {
      id: 'settings',
      label: t('nav.settings'),
      icon: Settings,
      path: '/settings',
      subItems: [
        { label: t('nav.sub.general'), icon: Wrench, path: '/settings/general' },
        { label: t('nav.sub.security'), icon: Shield, path: '/settings/security' },
        { label: t('nav.sub.billing'), icon: CreditCard, path: '/settings/billing' },
      ],
    },
  ];

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
          </div>
          <LanguageSwitcher />
        </div>

        {/* Navigation Menu */}
        <nav className="flex-1 overflow-y-auto py-4">
          <ul className="space-y-1 px-2">
            {menuItems.map((item) => (
              <li key={item.id}>
                <div>
                  {item.subItems.length === 0 ? (
                    // Direct link for items without sub-items
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
                          <item.icon className="h-5 w-5" />
                          <span>{item.label}</span>
                        </div>
                        {expandedMenus[item.id] ? (
                          <ChevronDown className="h-4 w-4" />
                        ) : (
                          <ChevronRight className="h-4 w-4" />
                        )}
                      </button>

                      {/* Sub-menu */}
                      {expandedMenus[item.id] && (
                        <ul className="mt-1 ml-4 space-y-1">
                          {item.subItems.map((subItem, idx) => (
                            <li key={idx}>
                              <Link
                                to={subItem.path}
                                className={`flex items-center space-x-3 px-3 py-2 text-sm rounded-lg transition-colors ${
                                  isActive(subItem.path)
                                    ? 'bg-blue-50 text-blue-700'
                                    : 'text-gray-600 hover:bg-gray-100'
                                }`}
                              >
                                <subItem.icon className="h-4 w-4" />
                                <span>{subItem.label}</span>
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
    </div>
  );
}
