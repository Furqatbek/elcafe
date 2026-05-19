import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI, restaurantAPI, customerAPI, financialAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Package,
  ShoppingCart,
  TrendingUp,
  TrendingDown,
  DollarSign,
  CreditCard,
  Wallet,
  ArrowUpRight,
  ArrowDownRight,
  Calendar,
  RefreshCw,
  AlertTriangle,
  AlertCircle,
  ExternalLink
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { format, subDays, startOfMonth, startOfWeek, subWeeks, subMonths, endOfMonth, endOfWeek } from 'date-fns';

export default function Dashboard() {
  const { t } = useTranslation();
  const [stats, setStats] = useState({
    pendingOrders: 0,
    restaurants: 0,
    totalCustomers: 0,
    loading: true,
  });
  const [dashboardData, setDashboardData] = useState(null);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [restaurants, setRestaurants] = useState([]);
  const [period, setPeriod] = useState('month');
  const [customStartDate, setCustomStartDate] = useState('');
  const [customEndDate, setCustomEndDate] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadInitialData();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadDashboardData();
    }
  }, [selectedRestaurant, period, customStartDate, customEndDate]);

  const loadInitialData = async () => {
    try {
      const [ordersRes, restaurantsRes, customersRes] = await Promise.all([
        orderAPI.getPending(),
        restaurantAPI.getActive(),
        customerAPI.getAll({ page: 0, size: 1 }),
      ]);

      const restaurantList = restaurantsRes.data.data || [];
      setRestaurants(restaurantList);

      if (restaurantList.length > 0) {
        setSelectedRestaurant(restaurantList[0].id);
      }

      setStats({
        pendingOrders: ordersRes.data.data?.length || 0,
        restaurants: restaurantList.length,
        totalCustomers: customersRes.data.data?.totalElements || 0,
        loading: false,
      });
    } catch (error) {
      console.error('Failed to load initial data:', error);
      setStats((prev) => ({ ...prev, loading: false }));
    }
  };

  const getDateRangeForPeriod = (periodValue) => {
    const today = new Date();
    let startDate, endDate;

    switch (periodValue) {
      case 'today':
        startDate = today;
        endDate = today;
        break;
      case 'yesterday':
        startDate = subDays(today, 1);
        endDate = subDays(today, 1);
        break;
      case 'week':
        startDate = startOfWeek(today, { weekStartsOn: 1 });
        endDate = today;
        break;
      case 'lastWeek': {
        const lastWeekStart = startOfWeek(subWeeks(today, 1), { weekStartsOn: 1 });
        startDate = lastWeekStart;
        endDate = endOfWeek(lastWeekStart, { weekStartsOn: 1 });
        break;
      }
      case 'month':
        startDate = startOfMonth(today);
        endDate = today;
        break;
      case 'lastMonth': {
        const lastMonthStart = startOfMonth(subMonths(today, 1));
        startDate = lastMonthStart;
        endDate = endOfMonth(lastMonthStart);
        break;
      }
      case 'custom':
        if (customStartDate && customEndDate) {
          startDate = new Date(customStartDate);
          endDate = new Date(customEndDate);
        } else {
          return null;
        }
        break;
      default:
        startDate = startOfMonth(today);
        endDate = today;
    }

    return {
      startDate: format(startDate, 'yyyy-MM-dd'),
      endDate: format(endDate, 'yyyy-MM-dd'),
    };
  };

  const loadDashboardData = async () => {
    if (!selectedRestaurant) return;

    // For custom period, validate dates
    if (period === 'custom' && (!customStartDate || !customEndDate)) {
      return;
    }

    setLoading(true);
    try {
      let response;

      // Use smart endpoints for today/week/month that handle business day logic
      // These endpoints use getCurrentBusinessDay() to handle overnight shifts correctly
      // e.g., at 4 AM with 21:00-03:00 shift, "today" returns yesterday's business day
      switch (period) {
        case 'today':
          response = await financialAPI.getTodaySummary(selectedRestaurant);
          break;
        case 'week':
          response = await financialAPI.getWeekSummary(selectedRestaurant);
          break;
        case 'month':
          response = await financialAPI.getMonthSummary(selectedRestaurant);
          break;
        default: {
          // For yesterday, lastWeek, lastMonth, custom - use date range endpoint
          const dateRange = getDateRangeForPeriod(period);
          if (!dateRange) {
            setLoading(false);
            return;
          }
          response = await financialAPI.getDashboard(
            selectedRestaurant,
            dateRange.startDate,
            dateRange.endDate
          );
        }
      }

      setDashboardData(response.data.data);
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
    } finally {
      setLoading(false);
    }
  };

  const formatCurrency = (amount) => {
    if (!amount) return '0.00';
    return Number(amount).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  };

  const formatPercentage = (value) => {
    if (!value) return '0%';
    const num = Number(value);
    const prefix = num >= 0 ? '+' : '';
    return `${prefix}${num.toFixed(1)}%`;
  };

  const getTrendIcon = (value) => {
    if (!value) return null;
    return Number(value) >= 0
      ? <ArrowUpRight className="h-4 w-4 text-green-500" />
      : <ArrowDownRight className="h-4 w-4 text-red-500" />;
  };

  const getTrendColor = (value) => {
    if (!value) return 'text-gray-500';
    return Number(value) >= 0 ? 'text-green-600' : 'text-red-600';
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('dashboard.title', 'Dashboard')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('dashboard.financialOverview', 'Financial overview and analytics')}
          </p>
        </div>
        <div className="flex gap-4">
          <Select value={selectedRestaurant?.toString()} onValueChange={(v) => setSelectedRestaurant(Number(v))}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('dashboard.selectRestaurant', 'Select restaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map((r) => (
                <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={period} onValueChange={setPeriod}>
            <SelectTrigger className="w-[150px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="today">{t('dashboard.today', 'Today')}</SelectItem>
              <SelectItem value="yesterday">{t('dashboard.yesterday', 'Yesterday')}</SelectItem>
              <SelectItem value="week">{t('dashboard.thisWeek', 'This Week')}</SelectItem>
              <SelectItem value="lastWeek">{t('dashboard.lastWeek', 'Last Week')}</SelectItem>
              <SelectItem value="month">{t('dashboard.thisMonth', 'This Month')}</SelectItem>
              <SelectItem value="lastMonth">{t('dashboard.lastMonth', 'Last Month')}</SelectItem>
              <SelectItem value="custom">{t('dashboard.custom', 'Custom Range')}</SelectItem>
            </SelectContent>
          </Select>
          {period === 'custom' && (
            <div className="flex gap-2 items-center">
              <input
                type="date"
                value={customStartDate}
                onChange={(e) => setCustomStartDate(e.target.value)}
                className="px-3 py-2 border rounded-md text-sm"
              />
              <span className="text-muted-foreground">-</span>
              <input
                type="date"
                value={customEndDate}
                onChange={(e) => setCustomEndDate(e.target.value)}
                className="px-3 py-2 border rounded-md text-sm"
              />
            </div>
          )}
          <Button variant="outline" size="icon" onClick={loadDashboardData}>
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {/* Financial Summary Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard.totalIncome', 'Total Income')}</CardTitle>
            <DollarSign className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">
              {loading ? '...' : formatCurrency(dashboardData?.totalIncome)}
            </div>
            {dashboardData?.comparison && (
              <div className="flex items-center gap-1 mt-1">
                {getTrendIcon(dashboardData.comparison.incomeChange)}
                <span className={`text-xs ${getTrendColor(dashboardData.comparison.incomeChange)}`}>
                  {formatPercentage(dashboardData.comparison.incomeChange)} {t('dashboard.vsPrevious', 'vs previous')}
                </span>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard.totalExpenses', 'Total Expenses')}</CardTitle>
            <Wallet className="h-4 w-4 text-red-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">
              {loading ? '...' : formatCurrency(dashboardData?.totalExpenses)}
            </div>
            {dashboardData?.totalPayroll > 0 && (
              <p className="text-xs text-muted-foreground mt-1">
                {t('dashboard.payroll', 'Payroll')}: {formatCurrency(dashboardData.totalPayroll)}
              </p>
            )}
            {dashboardData?.comparison && (
              <div className="flex items-center gap-1 mt-1">
                {getTrendIcon(-dashboardData.comparison.expenseChange)}
                <span className={`text-xs ${getTrendColor(-dashboardData.comparison.expenseChange)}`}>
                  {formatPercentage(dashboardData.comparison.expenseChange)} {t('dashboard.vsPrevious', 'vs previous')}
                </span>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard.netProfit', 'Net Profit')}</CardTitle>
            {Number(dashboardData?.netProfit) >= 0
              ? <TrendingUp className="h-4 w-4 text-green-600" />
              : <TrendingDown className="h-4 w-4 text-red-600" />
            }
          </CardHeader>
          <CardContent>
            <div className={`text-2xl font-bold ${Number(dashboardData?.netProfit) >= 0 ? 'text-green-600' : 'text-red-600'}`}>
              {loading ? '...' : formatCurrency(dashboardData?.netProfit)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('dashboard.profitMargin', 'Margin')}: {dashboardData?.profitMargin?.toFixed(1) || '0'}%
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard.totalOrders', 'Total Orders')}</CardTitle>
            <ShoppingCart className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {loading ? '...' : dashboardData?.orderStats?.totalOrders || 0}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('dashboard.avgOrder', 'Avg')}: {formatCurrency(dashboardData?.orderStats?.averageOrderValue)}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Quick Stats Row */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card className="bg-green-50">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-green-700">{t('dashboard.completedOrders', 'Completed')}</p>
                <p className="text-2xl font-bold text-green-800">{dashboardData?.orderStats?.completedOrders || 0}</p>
              </div>
              <Package className="h-8 w-8 text-green-600" />
            </div>
          </CardContent>
        </Card>
        <Card className="bg-red-50">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-red-700">{t('dashboard.cancelledOrders', 'Cancelled')}</p>
                <p className="text-2xl font-bold text-red-800">{dashboardData?.orderStats?.cancelledOrders || 0}</p>
              </div>
              <Package className="h-8 w-8 text-red-600" />
            </div>
          </CardContent>
        </Card>
        <Card className="bg-blue-50">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-blue-700">{t('dashboard.itemsSold', 'Items Sold')}</p>
                <p className="text-2xl font-bold text-blue-800">{dashboardData?.orderStats?.totalItemsSold || 0}</p>
              </div>
              <ShoppingCart className="h-8 w-8 text-blue-600" />
            </div>
          </CardContent>
        </Card>
        <Card className="bg-purple-50">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-purple-700">{t('dashboard.pendingOrders', 'Pending')}</p>
                <p className="text-2xl font-bold text-purple-800">{stats.pendingOrders}</p>
              </div>
              <Package className="h-8 w-8 text-purple-600" />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Inventory Alerts Section */}
      {dashboardData?.inventoryAlerts && (dashboardData.inventoryAlerts.lowStockCount > 0 || dashboardData.inventoryAlerts.reorderCount > 0) && (
        <Card className="border-orange-200 bg-orange-50">
          <CardHeader className="pb-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-5 w-5 text-orange-600" />
                <CardTitle className="text-orange-800">{t('dashboard.inventoryAlerts', 'Inventory Alerts')}</CardTitle>
              </div>
              <Link
                to="/kitchen/inventory"
                className="flex items-center gap-1 text-sm text-orange-600 hover:text-orange-800"
              >
                {t('dashboard.viewAll', 'View All')}
                <ExternalLink className="h-4 w-4" />
              </Link>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4 md:grid-cols-3 mb-4">
              <div className="flex items-center gap-3 p-3 bg-red-100 rounded-lg">
                <AlertCircle className="h-8 w-8 text-red-600" />
                <div>
                  <p className="text-2xl font-bold text-red-700">{dashboardData.inventoryAlerts.lowStockCount}</p>
                  <p className="text-sm text-red-600">{t('dashboard.lowStock', 'Low Stock')}</p>
                </div>
              </div>
              <div className="flex items-center gap-3 p-3 bg-yellow-100 rounded-lg">
                <TrendingDown className="h-8 w-8 text-yellow-600" />
                <div>
                  <p className="text-2xl font-bold text-yellow-700">{dashboardData.inventoryAlerts.reorderCount}</p>
                  <p className="text-sm text-yellow-600">{t('dashboard.needsReorder', 'Needs Reorder')}</p>
                </div>
              </div>
              <div className="flex items-center gap-3 p-3 bg-orange-100 rounded-lg">
                <Calendar className="h-8 w-8 text-orange-600" />
                <div>
                  <p className="text-2xl font-bold text-orange-700">{dashboardData.inventoryAlerts.expiringCount || 0}</p>
                  <p className="text-sm text-orange-600">{t('dashboard.expiringSoon', 'Expiring Soon')}</p>
                </div>
              </div>
            </div>

            {dashboardData.inventoryAlerts.lowStockItems?.length > 0 && (
              <div className="mt-4">
                <h4 className="text-sm font-semibold text-orange-800 mb-2">
                  {t('dashboard.criticalItems', 'Critical Items')}
                </h4>
                <div className="space-y-2">
                  {dashboardData.inventoryAlerts.lowStockItems.map((item) => (
                    <div
                      key={item.ingredientId}
                      className={`flex items-center justify-between p-2 rounded-lg ${
                        item.alertLevel === 'CRITICAL' ? 'bg-red-100' : 'bg-yellow-100'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <span className={`w-2 h-2 rounded-full ${
                          item.alertLevel === 'CRITICAL' ? 'bg-red-500' : 'bg-yellow-500'
                        }`} />
                        <span className="font-medium">{item.ingredientName}</span>
                        {item.supplierName && (
                          <span className="text-xs text-gray-500">({item.supplierName})</span>
                        )}
                      </div>
                      <div className="flex items-center gap-4">
                        <span className={`text-sm font-semibold ${
                          item.alertLevel === 'CRITICAL' ? 'text-red-700' : 'text-yellow-700'
                        }`}>
                          {item.currentStock} / {item.minimumStock} {item.unit}
                        </span>
                        <Link
                          to={`/purchase-orders`}
                          state={{ prefillData: { ingredientId: item.ingredientId, ingredientName: item.ingredientName } }}
                          className="text-xs px-2 py-1 bg-blue-500 text-white rounded hover:bg-blue-600"
                        >
                          {t('dashboard.createPO', 'Create PO')}
                        </Link>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {/* Income by Order Type */}
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.incomeByOrderType', 'Income by Order Type')}</CardTitle>
          </CardHeader>
          <CardContent>
            {dashboardData?.incomeByOrderType ? (
              <div className="space-y-4">
                {Object.entries(dashboardData.incomeByOrderType).map(([type, amount]) => (
                  <div key={type} className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <div className={`w-3 h-3 rounded-full ${
                        type === 'DELIVERY' ? 'bg-blue-500' :
                        type === 'DINE_IN' ? 'bg-green-500' :
                        type === 'TAKEAWAY' ? 'bg-orange-500' : 'bg-gray-500'
                      }`} />
                      <span className="capitalize">{type.replace('_', ' ')}</span>
                    </div>
                    <span className="font-semibold">{formatCurrency(amount)}</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-muted-foreground text-sm">{t('common.noData', 'No data available')}</p>
            )}
          </CardContent>
        </Card>

        {/* Expenses by Category */}
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.expensesByCategory', 'Expenses by Category')}</CardTitle>
          </CardHeader>
          <CardContent>
            {dashboardData?.expensesByCategory && Object.keys(dashboardData.expensesByCategory).length > 0 ? (
              <div className="space-y-4">
                {Object.entries(dashboardData.expensesByCategory).map(([category, amount]) => (
                  <div key={category} className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <div className={`w-3 h-3 rounded-full ${
                        category === 'PAYROLL' ? 'bg-purple-500' :
                        category === 'RENT' ? 'bg-red-500' :
                        category === 'UTILITIES' ? 'bg-yellow-500' :
                        category === 'SUPPLIES' ? 'bg-blue-500' :
                        category === 'INVENTORY' ? 'bg-green-500' : 'bg-gray-500'
                      }`} />
                      <span className="capitalize">{category.replace('_', ' ')}</span>
                    </div>
                    <span className="font-semibold">{formatCurrency(amount)}</span>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-muted-foreground text-sm">{t('dashboard.noExpenses', 'No expenses recorded')}</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Sold Items */}
      <Card>
        <CardHeader>
          <CardTitle>{t('dashboard.soldItems', 'Sold Items')}</CardTitle>
          <CardDescription>{t('dashboard.soldItemsDesc', 'Products sold during the selected period')}</CardDescription>
        </CardHeader>
        <CardContent>
          {dashboardData?.soldItems?.length > 0 ? (
            <div className="overflow-x-auto max-h-96 overflow-y-auto">
              <table className="w-full text-sm">
                <thead className="sticky top-0 bg-white">
                  <tr className="border-b">
                    <th className="text-left py-2 pr-4">#</th>
                    <th className="text-left py-2">{t('dashboard.product', 'Product')}</th>
                    <th className="text-right py-2">{t('dashboard.qtySold', 'Qty')}</th>
                    <th className="text-right py-2">{t('dashboard.revenue', 'Revenue')}</th>
                    <th className="text-right py-2">{t('dashboard.cost', 'Cost')}</th>
                    <th className="text-right py-2">{t('dashboard.profit', 'Profit')}</th>
                    <th className="text-right py-2">{t('dashboard.margin', 'Margin')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboardData.soldItems.map((item, index) => (
                    <tr key={item.productId} className="border-b hover:bg-muted/50">
                      <td className="py-2 pr-4">
                        <span className={`w-6 h-6 rounded-full flex items-center justify-center text-white text-xs font-bold ${
                          index === 0 ? 'bg-yellow-500' :
                          index === 1 ? 'bg-gray-400' :
                          index === 2 ? 'bg-orange-400' : 'bg-blue-400'
                        }`}>
                          {index + 1}
                        </span>
                      </td>
                      <td className="py-2">
                        <p className="font-medium">{item.productName}</p>
                      </td>
                      <td className="text-right py-2">{item.quantitySold}</td>
                      <td className="text-right py-2 text-green-600 font-medium">{formatCurrency(item.totalRevenue)}</td>
                      <td className="text-right py-2 text-gray-600">
                        {item.totalCost ? formatCurrency(item.totalCost) : '-'}
                      </td>
                      <td className={`text-right py-2 font-medium ${
                        item.profit && Number(item.profit) >= 0 ? 'text-green-600' : 'text-red-600'
                      }`}>
                        {item.profit ? formatCurrency(item.profit) : '-'}
                      </td>
                      <td className="text-right py-2">
                        {item.profitMargin ? (
                          <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                            Number(item.profitMargin) >= 30 ? 'bg-green-100 text-green-700' :
                            Number(item.profitMargin) >= 15 ? 'bg-yellow-100 text-yellow-700' :
                            'bg-red-100 text-red-700'
                          }`}>
                            {Number(item.profitMargin).toFixed(1)}%
                          </span>
                        ) : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">{t('common.noData', 'No data available')}</p>
          )}
        </CardContent>
      </Card>

      {/* Daily Stats */}
      {dashboardData?.dailyStats?.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>{t('dashboard.dailyPerformance', 'Daily Performance')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-2">{t('dashboard.date', 'Date')}</th>
                    <th className="text-right py-2">{t('dashboard.orders', 'Orders')}</th>
                    <th className="text-right py-2">{t('dashboard.income', 'Income')}</th>
                    <th className="text-right py-2">{t('dashboard.expenses', 'Expenses')}</th>
                    <th className="text-right py-2">{t('dashboard.profit', 'Profit')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboardData.dailyStats.map((day) => (
                    <tr key={day.date} className="border-b hover:bg-muted/50">
                      <td className="py-2">{format(new Date(day.date), 'MMM dd, yyyy')}</td>
                      <td className="text-right py-2">{day.orderCount}</td>
                      <td className="text-right py-2 text-green-600">{formatCurrency(day.income)}</td>
                      <td className="text-right py-2 text-red-600">{formatCurrency(day.expenses)}</td>
                      <td className={`text-right py-2 font-semibold ${Number(day.netProfit) >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                        {formatCurrency(day.netProfit)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Payment Methods */}
      <Card>
        <CardHeader>
          <CardTitle>{t('dashboard.paymentMethods', 'Income by Payment Method')}</CardTitle>
        </CardHeader>
        <CardContent>
          {dashboardData?.incomeByPaymentMethod ? (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {Object.entries(dashboardData.incomeByPaymentMethod).map(([method, amount]) => (
                <div key={method} className="p-4 bg-muted rounded-lg">
                  <div className="flex items-center gap-2 mb-2">
                    {method === 'CASH' ? <DollarSign className="h-4 w-4" /> :
                     method.includes('CARD') ? <CreditCard className="h-4 w-4" /> :
                     <Wallet className="h-4 w-4" />}
                    <span className="text-sm text-muted-foreground capitalize">{method.replace('_', ' ')}</span>
                  </div>
                  <p className="text-xl font-bold">{formatCurrency(amount)}</p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">{t('common.noData', 'No data available')}</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
