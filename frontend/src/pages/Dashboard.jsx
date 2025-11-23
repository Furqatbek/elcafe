import { useEffect, useState } from 'react';
import { orderAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Package, Users, ShoppingCart, Store } from 'lucide-react';

export default function Dashboard() {
  const [stats, setStats] = useState({
    pendingOrders: 0,
    restaurants: 0,
    loading: true,
  });

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    try {
      const [ordersRes, restaurantsRes] = await Promise.all([
        orderAPI.getPending(),
        restaurantAPI.getActive(),
      ]);

      setStats({
        pendingOrders: ordersRes.data.data.length,
        restaurants: restaurantsRes.data.data.length,
        loading: false,
      });
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
      setStats((prev) => ({ ...prev, loading: false }));
    }
  };

  const statCards = [
    {
      title: 'Pending Orders',
      value: stats.pendingOrders,
      description: 'Orders awaiting processing',
      icon: Package,
      color: 'text-blue-600',
    },
    {
      title: 'Active Restaurants',
      value: stats.restaurants,
      description: 'Currently accepting orders',
      icon: Store,
      color: 'text-green-600',
    },
    {
      title: 'Total Orders',
      value: '0',
      description: 'Today',
      icon: ShoppingCart,
      color: 'text-purple-600',
    },
    {
      title: 'Total Customers',
      value: '0',
      description: 'Registered users',
      icon: Users,
      color: 'text-orange-600',
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground mt-1">
          Overview of your restaurant delivery system
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
            <CardTitle>Recent Activity</CardTitle>
            <CardDescription>Latest orders and updates</CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              No recent activity to display
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Quick Actions</CardTitle>
            <CardDescription>Common tasks and operations</CardDescription>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-sm text-muted-foreground">
              Navigate to Orders or Restaurants to get started
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
