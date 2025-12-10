import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { analyticsAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import {
  Package,
  TrendingUp,
  TrendingDown,
  BarChart3,
  AlertTriangle,
  Calendar,
  DollarSign,
  Activity,
} from 'lucide-react';

export default function InventoryAnalytics() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [dateRange, setDateRange] = useState({
    startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0],
  });

  const [data, setData] = useState({
    turnover: null,
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

      const turnoverRes = await analyticsAPI.getInventoryTurnover(params);

      setData({
        turnover: turnoverRes.data.data,
      });
    } catch (error) {
      console.error('Failed to load inventory analytics:', error);
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

  const getTurnoverStatus = (ratio) => {
    if (ratio > 12) return { label: t('analytics.inventory.excellent'), color: 'text-green-600', bg: 'bg-green-50' };
    if (ratio > 6) return { label: t('analytics.inventory.good'), color: 'text-blue-600', bg: 'bg-blue-50' };
    if (ratio > 3) return { label: t('analytics.inventory.fair'), color: 'text-yellow-600', bg: 'bg-yellow-50' };
    return { label: t('analytics.inventory.poor'), color: 'text-red-600', bg: 'bg-red-50' };
  };

  const slowMovingItems = data.turnover?.ingredientTurnovers?.filter(item => item.turnoverRatio < 3) || [];
  const fastMovingItems = data.turnover?.ingredientTurnovers?.filter(item => item.turnoverRatio > 12) || [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('analytics.inventory.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('analytics.inventory.subtitle')}
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
              {t('analytics.inventory.turnoverRatio')}
            </CardTitle>
            <Activity className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {data.turnover?.overallTurnoverRatio?.toFixed(2) || '0.00'}x
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {getTurnoverStatus(data.turnover?.overallTurnoverRatio || 0).label}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.inventory.daysToSell')}
            </CardTitle>
            <TrendingDown className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {Math.round(data.turnover?.averageDaysToSellInventory || 0)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('common.days')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.inventory.inventoryValue')}
            </CardTitle>
            <DollarSign className="h-4 w-4 text-purple-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatCurrency(data.turnover?.averageInventoryValue)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('analytics.inventory.avgValue')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.inventory.cogs')}
            </CardTitle>
            <Package className="h-4 w-4 text-orange-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatCurrency(data.turnover?.costOfGoodsSold)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('analytics.inventory.inPeriod')}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Turnover Overview */}
      {data.turnover && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BarChart3 className="h-5 w-5" />
              {t('analytics.inventory.turnoverOverview')}
            </CardTitle>
            <CardDescription>
              {t('analytics.inventory.turnoverOverviewDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className={`p-4 rounded-lg ${getTurnoverStatus(data.turnover.overallTurnoverRatio).bg}`}>
                <p className="text-sm text-gray-600 mb-1">{t('analytics.inventory.overallTurnover')}</p>
                <p className={`text-2xl font-bold ${getTurnoverStatus(data.turnover.overallTurnoverRatio).color}`}>
                  {data.turnover.overallTurnoverRatio?.toFixed(2)}x
                </p>
                <p className="text-xs text-gray-600 mt-1">
                  {getTurnoverStatus(data.turnover.overallTurnoverRatio).label}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.inventory.avgDaysToSell')}</p>
                <p className="text-2xl font-bold">
                  {Math.round(data.turnover.averageDaysToSellInventory)} {t('common.days')}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.inventory.totalIngredients')}</p>
                <p className="text-2xl font-bold">{data.turnover.ingredientTurnovers?.length || 0}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Alerts */}
      {(slowMovingItems.length > 0 || fastMovingItems.length > 0) && (
        <div className="grid gap-4 md:grid-cols-2">
          {slowMovingItems.length > 0 && (
            <Card className="border-red-200">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-red-600">
                  <AlertTriangle className="h-5 w-5" />
                  {t('analytics.inventory.slowMoving')}
                </CardTitle>
                <CardDescription>
                  {t('analytics.inventory.slowMovingDesc')}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {slowMovingItems.slice(0, 5).map((item, idx) => (
                    <div key={idx} className="flex justify-between items-center p-2 bg-red-50 rounded">
                      <span className="text-sm font-medium">{item.ingredientName}</span>
                      <span className="text-sm text-red-600">
                        {item.turnoverRatio?.toFixed(2)}x
                      </span>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {fastMovingItems.length > 0 && (
            <Card className="border-green-200">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-green-600">
                  <TrendingUp className="h-5 w-5" />
                  {t('analytics.inventory.fastMoving')}
                </CardTitle>
                <CardDescription>
                  {t('analytics.inventory.fastMovingDesc')}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-2">
                  {fastMovingItems.slice(0, 5).map((item, idx) => (
                    <div key={idx} className="flex justify-between items-center p-2 bg-green-50 rounded">
                      <span className="text-sm font-medium">{item.ingredientName}</span>
                      <span className="text-sm text-green-600">
                        {item.turnoverRatio?.toFixed(2)}x
                      </span>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {/* Ingredient Turnover Details */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Package className="h-5 w-5" />
            {t('analytics.inventory.ingredientDetails')}
          </CardTitle>
          <CardDescription>
            {t('analytics.inventory.ingredientDetailsDesc')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-3 px-4 font-medium">{t('common.ingredient')}</th>
                  <th className="text-left py-3 px-4 font-medium">{t('common.category')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.inventory.used')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.inventory.avgStock')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.inventory.turnover')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.inventory.daysToSell')}</th>
                  <th className="text-right py-3 px-4 font-medium">{t('analytics.inventory.costValue')}</th>
                  <th className="text-left py-3 px-4 font-medium">{t('common.status')}</th>
                </tr>
              </thead>
              <tbody>
                {data.turnover?.ingredientTurnovers?.map((ingredient, idx) => {
                  const status = getTurnoverStatus(ingredient.turnoverRatio);
                  return (
                    <tr key={idx} className="border-b hover:bg-gray-50">
                      <td className="py-3 px-4 font-medium">{ingredient.ingredientName}</td>
                      <td className="py-3 px-4">{ingredient.category}</td>
                      <td className="py-3 px-4 text-right">
                        {ingredient.quantityUsed?.toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-right">
                        {ingredient.averageStock?.toFixed(2)}
                      </td>
                      <td className="py-3 px-4 text-right font-medium">
                        {ingredient.turnoverRatio?.toFixed(2)}x
                      </td>
                      <td className="py-3 px-4 text-right">
                        {ingredient.daysToSellInventory} {t('common.days')}
                      </td>
                      <td className="py-3 px-4 text-right">
                        {formatCurrency(ingredient.costValue)}
                      </td>
                      <td className="py-3 px-4">
                        <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${status.bg} ${status.color}`}>
                          {status.label}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Recommendations */}
      <Card className="border-blue-200 bg-blue-50/30">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-blue-700">
            <TrendingUp className="h-5 w-5" />
            {t('analytics.inventory.recommendations')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="space-y-2 text-sm">
            <li className="flex items-start gap-2">
              <span className="text-blue-600 font-bold">•</span>
              <span>{t('analytics.inventory.rec1')}</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600 font-bold">•</span>
              <span>{t('analytics.inventory.rec2')}</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600 font-bold">•</span>
              <span>{t('analytics.inventory.rec3')}</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="text-blue-600 font-bold">•</span>
              <span>{t('analytics.inventory.rec4')}</span>
            </li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}
