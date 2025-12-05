import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI, restaurantAPI, customerAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Package, Users, ShoppingCart, Store } from 'lucide-react';

export default function Dashboard() {
  const { t } = useTranslation();
  const [stats, setStats] = useState({
    pendingOrders: 0,
    restaurants: 0,
    totalCustomers: 0,
    loading: true,
  });

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    try {
      const [ordersRes, restaurantsRes, customersRes] = await Promise.all([
        orderAPI.getPending(),
        restaurantAPI.getActive(),
        customerAPI.getAll({ page: 0, size: 1 }),
      ]);

      setStats({
        pendingOrders: ordersRes.data.data.length,
        restaurants: restaurantsRes.data.data.length,
        totalCustomers: customersRes.data.data.totalElements || 0,
        loading: false,
      });
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
      setStats((prev) => ({ ...prev, loading: false }));
    }
  };

  const statCards = [
    {
      title: t('dashboard.stats.pendingOrders'),
      value: stats.pendingOrders,
      description: t('orders.statuses.NEW'),
      icon: Package,
      color: 'text-blue-600',
    },
    {
      title: t('dashboard.stats.activeRestaurants'),
      value: stats.restaurants,
      description: t('restaurants.acceptingOrders'),
      icon: Store,
      color: 'text-green-600',
    },
    {
      title: t('dashboard.stats.totalOrders'),
      value: stats.pendingOrders,
      description: t('dashboard.stats.todayRevenue'),
      icon: ShoppingCart,
      color: 'text-purple-600',
    },
    {
      title: t('dashboard.stats.totalCustomers'),
      value: stats.totalCustomers,
      description: t('customers.title'),
      icon: Users,
      color: 'text-orange-600',
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">{t('dashboard.title')}</h1>
        <p className="text-muted-foreground mt-1">
          {t('app.tagline')}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        {statCards.map((stat) => (
          <Card key={stat.title}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{stat.title}</CardTitle>
              <stat.icon className={`h-4 w-4 ${stat.color}`} />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {stats.loading ? '...' : stat.value}
              </div>
              <p className="text-xs text-muted-foreground mt-1">
                {stat.description}
              </p>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.recentActivity')}</CardTitle>
            <CardDescription>{t('dashboard.recentOrders')}</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              {t('common.noData')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('common.actions')}</CardTitle>
            <CardDescription>{t('dashboard.overview')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-sm text-muted-foreground">
              {t('common.loading')}
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
