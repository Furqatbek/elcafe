import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { analyticsAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import {
  Clock,
  TrendingUp,
  Users,
  Timer,
  Truck,
  BarChart3,
  Calendar,
  Activity,
} from 'lucide-react';

export default function OperationalAnalytics() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [dateRange, setDateRange] = useState({
    startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0],
  });

  const [data, setData] = useState({
    salesPerHour: [],
    peakHours: null,
    tableTurnover: null,
    orderTiming: null,
  });

  useEffect(() => {
    loadAnalytics();
  }, []);

  const loadAnalytics = async () => {
    setLoading(true);
    try {
      const params = {
        startDate: dateRange.startDate,
        endDate: dateRange.endDate,
      };

      const [salesPerHourRes, peakHoursRes, tableTurnoverRes, orderTimingRes] =
        await Promise.all([
          analyticsAPI.getSalesPerHour(params),
          analyticsAPI.getPeakHours(params),
          analyticsAPI.getTableTurnover(params),
          analyticsAPI.getOrderTiming(params),
        ]);

      setData({
        salesPerHour: salesPerHourRes.data.data || [],
        peakHours: peakHoursRes.data.data,
        tableTurnover: tableTurnoverRes.data.data,
        orderTiming: orderTimingRes.data.data,
      });
    } catch (error) {
      console.error('Failed to load operational analytics:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDateChange = (e) => {
    setDateRange({ ...dateRange, [e.target.name]: e.target.value });
  };

  const handleApplyFilter = () => {
    loadAnalytics();
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount || 0);
  };

  const formatTime = (minutes) => {
    if (!minutes) return '0 min';
    const hours = Math.floor(minutes / 60);
    const mins = Math.round(minutes % 60);
    return hours > 0 ? `${hours}h ${mins}m` : `${mins} min`;
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('analytics.operational.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('analytics.operational.subtitle')}
          </p>
        </div>
      </div>

      {/* Date Range Filter */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            {t('common.dateRange')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex gap-4 items-end">
            <div className="flex-1">
              <label className="text-sm font-medium mb-2 block">
                {t('common.startDate')}
              </label>
              <Input
                type="date"
                name="startDate"
                value={dateRange.startDate}
                onChange={handleDateChange}
              />
            </div>
            <div className="flex-1">
              <label className="text-sm font-medium mb-2 block">
                {t('common.endDate')}
              </label>
              <Input
                type="date"
                name="endDate"
                value={dateRange.endDate}
                onChange={handleDateChange}
              />
            </div>
            <Button onClick={handleApplyFilter} disabled={loading}>
              {loading ? t('common.loading') : t('common.apply')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Key Metrics */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.operational.avgPrepTime')}
            </CardTitle>
            <Timer className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatTime(data.orderTiming?.averagePreparationTimeMinutes)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('analytics.operational.median')}: {formatTime(data.orderTiming?.medianPreparationTimeMinutes)}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.operational.avgDeliveryTime')}
            </CardTitle>
            <Truck className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatTime(data.orderTiming?.averageDeliveryTimeMinutes)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {data.orderTiming?.percentageDeliveryUnder30Min?.toFixed(1)}% {t('analytics.operational.under30min')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.operational.tableTurnover')}
            </CardTitle>
            <Users className="h-4 w-4 text-purple-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {data.tableTurnover?.averageTurnoverRate?.toFixed(2) || '0'} x
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {data.tableTurnover?.averageOccupancyRate?.toFixed(1)}% {t('analytics.operational.occupancy')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.operational.peakHours')}
            </CardTitle>
            <Activity className="h-4 w-4 text-orange-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {data.peakHours?.peakHours?.length || 0} {t('analytics.operational.hours')}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {data.peakHours?.peakHoursPercentage?.toFixed(1)}% {t('analytics.operational.ofOrders')}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Order Timing Details */}
      {data.orderTiming && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Clock className="h-5 w-5" />
              {t('analytics.operational.orderTimingDetails')}
            </CardTitle>
            <CardDescription>
              {t('analytics.operational.orderTimingDetailsDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-4">
                <h3 className="font-semibold text-sm text-gray-700">
                  {t('analytics.operational.preparationTime')}
                </h3>
                <div className="space-y-2">
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.average')}:</span>
                    <span className="font-medium">
                      {formatTime(data.orderTiming.averagePreparationTimeMinutes)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.median')}:</span>
                    <span className="font-medium">
                      {formatTime(data.orderTiming.medianPreparationTimeMinutes)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.min')}:</span>
                    <span className="font-medium text-green-600">
                      {formatTime(data.orderTiming.minPreparationTimeMinutes)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.max')}:</span>
                    <span className="font-medium text-red-600">
                      {formatTime(data.orderTiming.maxPreparationTimeMinutes)}
                    </span>
                  </div>
                  <div className="pt-2 border-t">
                    <div className="flex justify-between">
                      <span className="text-sm text-gray-600">{t('analytics.operational.under15min')}:</span>
                      <span className="font-medium text-green-600">
                        {data.orderTiming.percentagePreparationUnder15Min?.toFixed(1)}%
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-4">
                <h3 className="font-semibold text-sm text-gray-700">
                  {t('analytics.operational.dineInWaitTime')}
                </h3>
                <div className="space-y-2">
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.average')}:</span>
                    <span className="font-medium">
                      {formatTime(data.orderTiming.averageDineInWaitTimeMinutes)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.median')}:</span>
                    <span className="font-medium">
                      {formatTime(data.orderTiming.medianDineInWaitTimeMinutes)}
                    </span>
                  </div>
                </div>
              </div>

              <div className="space-y-4">
                <h3 className="font-semibold text-sm text-gray-700">
                  {t('analytics.operational.deliveryTime')}
                </h3>
                <div className="space-y-2">
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.average')}:</span>
                    <span className="font-medium">
                      {formatTime(data.orderTiming.averageDeliveryTimeMinutes)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.median')}:</span>
                    <span className="font-medium">
                      {formatTime(data.orderTiming.medianDeliveryTimeMinutes)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.min')}:</span>
                    <span className="font-medium text-green-600">
                      {formatTime(data.orderTiming.minDeliveryTimeMinutes)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600">{t('common.max')}:</span>
                    <span className="font-medium text-red-600">
                      {formatTime(data.orderTiming.maxDeliveryTimeMinutes)}
                    </span>
                  </div>
                  <div className="pt-2 border-t">
                    <div className="flex justify-between">
                      <span className="text-sm text-gray-600">{t('analytics.operational.under30min')}:</span>
                      <span className="font-medium text-green-600">
                        {data.orderTiming.percentageDeliveryUnder30Min?.toFixed(1)}%
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-4 pt-4 border-t">
              <p className="text-sm text-gray-600">
                {t('analytics.operational.totalOrdersAnalyzed')}: {data.orderTiming.totalOrdersAnalyzed}
              </p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Sales Per Hour */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BarChart3 className="h-5 w-5" />
            {t('analytics.operational.salesPerHour')}
          </CardTitle>
          <CardDescription>
            {t('analytics.operational.salesPerHourDesc')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-3 px-4 font-medium">{t('common.hour')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.operational.revenue')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.operational.orders')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.operational.avgOrderValue')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.operational.percentage')}</th>
                </tr>
              </thead>
              <tbody>
                {data.salesPerHour.map((hour, idx) => {
                  const isPeak = data.peakHours?.peakHours?.includes(hour.hour);
                  return (
                    <tr
                      key={idx}
                      className={`border-b hover:bg-gray-50 ${
                        isPeak ? 'bg-yellow-50' : ''
                      }`}
                    >
                      <td className="py-3 px-4 font-medium">
                        {hour.hour}:00 {isPeak && '⭐'}
                      </td>
                      <td className="py-3 px-4 text-right">{formatCurrency(hour.totalRevenue)}</td>
                      <td className="py-3 px-4 text-right">{hour.totalOrders}</td>
                      <td className="py-3 px-4 text-right">
                        {formatCurrency(hour.averageOrderValue)}
                      </td>
                      <td className="py-3 px-4 text-right">
                        {hour.percentageOfDailyRevenue?.toFixed(2)}%
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Peak Hours Details */}
      {data.peakHours && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5" />
              {t('analytics.operational.peakHoursAnalysis')}
            </CardTitle>
            <CardDescription>
              {t('analytics.operational.peakHoursAnalysisDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="p-4 bg-yellow-50 rounded-lg border border-yellow-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.peakHours')}</p>
                <p className="text-xl font-bold text-yellow-700">
                  {data.peakHours.peakHours?.join(', ')}:00
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.peakStart')}</p>
                <p className="text-xl font-bold">
                  {data.peakHours.averagePeakStart}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.peakEnd')}</p>
                <p className="text-xl font-bold">
                  {data.peakHours.averagePeakEnd}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.peakPercentage')}</p>
                <p className="text-xl font-bold text-green-600">
                  {data.peakHours.peakHoursPercentage?.toFixed(1)}%
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Table Turnover Details */}
      {data.tableTurnover && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Users className="h-5 w-5" />
              {t('analytics.operational.tableTurnoverAnalysis')}
            </CardTitle>
            <CardDescription>
              {t('analytics.operational.tableTurnoverAnalysisDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.totalTables')}</p>
                <p className="text-xl font-bold">{data.tableTurnover.totalTables}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.totalSeats')}</p>
                <p className="text-xl font-bold">{data.tableTurnover.totalSeats}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.dineInOrders')}</p>
                <p className="text-xl font-bold">{data.tableTurnover.totalDineInOrders}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.turnoverRate')}</p>
                <p className="text-xl font-bold text-blue-600">
                  {data.tableTurnover.averageTurnoverRate?.toFixed(2)}x
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.occupancyRate')}</p>
                <p className="text-xl font-bold text-green-600">
                  {data.tableTurnover.averageOccupancyRate?.toFixed(1)}%
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.operatingHours')}</p>
                <p className="text-xl font-bold">{data.tableTurnover.totalOperatingHours}h</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg col-span-2">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.operational.ordersPerSeat')}</p>
                <p className="text-xl font-bold">
                  {data.tableTurnover.ordersPerSeatPerDay?.toFixed(2)} / {t('common.day')}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
