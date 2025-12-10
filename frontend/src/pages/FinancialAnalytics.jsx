import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { analyticsAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import {
  DollarSign,
  TrendingUp,
  TrendingDown,
  Package,
  Percent,
  PieChart,
  BarChart3,
  Calendar,
} from 'lucide-react';

export default function FinancialAnalytics() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [dateRange, setDateRange] = useState({
    startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0],
  });

  const [data, setData] = useState({
    dailyRevenue: [],
    salesByCategory: [],
    cogs: null,
    profitability: null,
    contributionMargins: [],
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

      const [dailyRevenueRes, salesByCategoryRes, cogsRes, profitabilityRes, marginsRes] =
        await Promise.all([
          analyticsAPI.getDailyRevenue(params),
          analyticsAPI.getSalesByCategory(params),
          analyticsAPI.getCOGS(params),
          analyticsAPI.getProfitability({ ...params, laborCosts: 0, operatingExpenses: 0 }),
          analyticsAPI.getContributionMargins(params),
        ]);

      setData({
        dailyRevenue: dailyRevenueRes.data.data || [],
        salesByCategory: salesByCategoryRes.data.data || [],
        cogs: cogsRes.data.data,
        profitability: profitabilityRes.data.data,
        contributionMargins: marginsRes.data.data || [],
      });
    } catch (error) {
      console.error('Failed to load financial analytics:', error);
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

  const formatPercent = (percent) => {
    return `${(percent || 0).toFixed(2)}%`;
  };

  // Calculate totals
  const totalRevenue = data.dailyRevenue.reduce((sum, day) => sum + (day.totalRevenue || 0), 0);
  const totalOrders = data.dailyRevenue.reduce((sum, day) => sum + (day.totalOrders || 0), 0);
  const avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('analytics.financial.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('analytics.financial.subtitle')}
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
              {t('analytics.financial.totalRevenue')}
            </CardTitle>
            <DollarSign className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formatCurrency(totalRevenue)}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('analytics.financial.fromOrders', { count: totalOrders })}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.financial.avgOrderValue')}
            </CardTitle>
            <TrendingUp className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formatCurrency(avgOrderValue)}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('analytics.financial.perOrder')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.financial.grossProfit')}
            </CardTitle>
            <PieChart className="h-4 w-4 text-purple-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatCurrency(data.profitability?.grossProfit || 0)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {formatPercent(data.profitability?.grossProfitMargin || 0)} {t('common.margin')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.financial.foodCost')}
            </CardTitle>
            <Percent className="h-4 w-4 text-orange-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatPercent(data.cogs?.foodCostPercentage || 0)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {formatCurrency(data.cogs?.totalCOGS || 0)} {t('analytics.financial.cogs')}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Daily Revenue Table */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BarChart3 className="h-5 w-5" />
            {t('analytics.financial.dailyRevenue')}
          </CardTitle>
          <CardDescription>
            {t('analytics.financial.dailyRevenueDesc')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-3 px-4 font-medium">{t('common.date')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.revenue')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.orders')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.aov')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('common.cash')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('common.card')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('common.online')}</th>
                </tr>
              </thead>
              <tbody>
                {data.dailyRevenue.map((day, idx) => (
                  <tr key={idx} className="border-b hover:bg-gray-50">
                    <td className="py-3 px-4">{day.date}</td>
                    <td className="py-3 px-4 text-right font-medium">
                      {formatCurrency(day.totalRevenue)}
                    </td>
                    <td className="py-3 px-4 text-right">{day.totalOrders}</td>
                    <td className="py-3 px-4 text-right">
                      {formatCurrency(day.averageOrderValue)}
                    </td>
                    <td className="py-3 px-4 text-right text-green-600">
                      {formatCurrency(day.cashRevenue)}
                    </td>
                    <td className="py-3 px-4 text-right text-blue-600">
                      {formatCurrency(day.cardRevenue)}
                    </td>
                    <td className="py-3 px-4 text-right text-purple-600">
                      {formatCurrency(day.onlineRevenue)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Sales by Category */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Package className="h-5 w-5" />
            {t('analytics.financial.salesByCategory')}
          </CardTitle>
          <CardDescription>
            {t('analytics.financial.salesByCategoryDesc')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-3 px-4 font-medium">{t('common.category')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.revenue')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.itemsSold')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.percentage')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.avgPrice')}</th>
                </tr>
              </thead>
              <tbody>
                {data.salesByCategory.map((category, idx) => (
                  <tr key={idx} className="border-b hover:bg-gray-50">
                    <td className="py-3 px-4 font-medium">{category.categoryName}</td>
                    <td className="py-3 px-4 text-right">{formatCurrency(category.totalRevenue)}</td>
                    <td className="py-3 px-4 text-right">{category.totalItemsSold}</td>
                    <td className="py-3 px-4 text-right">
                      {formatPercent(category.percentageOfTotalRevenue)}
                    </td>
                    <td className="py-3 px-4 text-right">{formatCurrency(category.averageItemPrice)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Contribution Margins */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <TrendingUp className="h-5 w-5" />
            {t('analytics.financial.contributionMargins')}
          </CardTitle>
          <CardDescription>
            {t('analytics.financial.contributionMarginsDesc')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-3 px-4 font-medium">{t('common.product')}</th>
                  <th className="text-left py-3 px-4 font-medium">{t('common.category')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.sellingPrice')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.costPrice')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.margin')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.unitsSold')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.financial.totalContribution')}</th>
                </tr>
              </thead>
              <tbody>
                {data.contributionMargins.slice(0, 10).map((item, idx) => (
                  <tr key={idx} className="border-b hover:bg-gray-50">
                    <td className="py-3 px-4 font-medium">{item.productName}</td>
                    <td className="py-3 px-4">{item.categoryName}</td>
                    <td className="py-3 px-4 text-right">{formatCurrency(item.sellingPrice)}</td>
                    <td className="py-3 px-4 text-right">{formatCurrency(item.costPrice)}</td>
                    <td className="py-3 px-4 text-right">
                      <span
                        className={`font-medium ${
                          item.contributionMarginRatio > 50
                            ? 'text-green-600'
                            : item.contributionMarginRatio > 30
                            ? 'text-yellow-600'
                            : 'text-red-600'
                        }`}
                      >
                        {formatPercent(item.contributionMarginRatio)}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">{item.unitsSold}</td>
                    <td className="py-3 px-4 text-right font-medium">
                      {formatCurrency(item.totalContribution)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Profitability Details */}
      {data.profitability && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingDown className="h-5 w-5" />
              {t('analytics.financial.profitabilityDetails')}
            </CardTitle>
            <CardDescription>
              {t('analytics.financial.profitabilityDetailsDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.totalRevenue')}</p>
                <p className="text-xl font-bold">{formatCurrency(data.profitability.totalRevenue)}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.totalCOGS')}</p>
                <p className="text-xl font-bold text-red-600">
                  {formatCurrency(data.profitability.totalCOGS)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.grossProfit')}</p>
                <p className="text-xl font-bold text-green-600">
                  {formatCurrency(data.profitability.grossProfit)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.grossMargin')}</p>
                <p className="text-xl font-bold">
                  {formatPercent(data.profitability.grossProfitMargin)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.laborCost')}</p>
                <p className="text-xl font-bold">{formatCurrency(data.profitability.totalLaborCost)}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.operatingExpenses')}</p>
                <p className="text-xl font-bold">
                  {formatCurrency(data.profitability.totalOperatingExpenses)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.netProfit')}</p>
                <p className="text-xl font-bold text-green-600">
                  {formatCurrency(data.profitability.netProfit)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.financial.netMargin')}</p>
                <p className="text-xl font-bold">
                  {formatPercent(data.profitability.netProfitMargin)}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
