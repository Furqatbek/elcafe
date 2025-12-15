import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { analyticsAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import {
  Users,
  TrendingUp,
  TrendingDown,
  Heart,
  Star,
  DollarSign,
  UserPlus,
  UserCheck,
  Calendar,
  Percent,
} from 'lucide-react';

export default function CustomerAnalytics() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [dateRange, setDateRange] = useState({
    startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0],
  });

  const [data, setData] = useState({
    retention: null,
    ltv: null,
    satisfaction: null,
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

      const [retentionRes, ltvRes, satisfactionRes] = await Promise.all([
        analyticsAPI.getCustomerRetention(params),
        analyticsAPI.getCustomerLTV({}),
        analyticsAPI.getCustomerSatisfaction(params),
      ]);

      setData({
        retention: retentionRes.data.data,
        ltv: ltvRes.data.data,
        satisfaction: satisfactionRes.data.data,
      });
    } catch (error) {
      console.error('Failed to load customer analytics:', error);
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

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('analytics.customer.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('analytics.customer.subtitle')}
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
              {t('analytics.customer.retentionRate')}
            </CardTitle>
            <Heart className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatPercent(data.retention?.retentionRate)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('analytics.customer.churn')}: {formatPercent(data.retention?.churnRate)}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.customer.avgLTV')}
            </CardTitle>
            <DollarSign className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatCurrency(data.ltv?.averageCustomerLTV)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('analytics.customer.median')}: {formatCurrency(data.ltv?.medianCustomerLTV)}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.customer.repeatCustomers')}
            </CardTitle>
            <UserCheck className="h-4 w-4 text-purple-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {formatPercent(data.retention?.repeatCustomerRate)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {data.retention?.repeatCustomers} / {data.retention?.repeatCustomers + data.retention?.oneTimeCustomers}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">
              {t('analytics.customer.satisfaction')}
            </CardTitle>
            <Star className="h-4 w-4 text-yellow-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {data.satisfaction?.overallSatisfactionScore?.toFixed(1) || '0.0'}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {data.satisfaction?.totalReviews || 0} {t('analytics.customer.reviews')}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Customer Retention Details */}
      {data.retention && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Heart className="h-5 w-5" />
              {t('analytics.customer.retentionDetails')}
            </CardTitle>
            <CardDescription>
              {t('analytics.customer.retentionDetailsDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.customersAtStart')}</p>
                <p className="text-xl font-bold">{data.retention.customersAtStart}</p>
              </div>
              <div className="p-4 bg-green-50 rounded-lg border border-green-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.newCustomers')}</p>
                <p className="text-xl font-bold text-green-600">{data.retention.newCustomers}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.customersAtEnd')}</p>
                <p className="text-xl font-bold">{data.retention.customersAtEnd}</p>
              </div>
              <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.returningCustomers')}</p>
                <p className="text-xl font-bold text-blue-600">{data.retention.returningCustomers}</p>
              </div>
              <div className="p-4 bg-green-50 rounded-lg border border-green-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.retentionRate')}</p>
                <p className="text-xl font-bold text-green-600">
                  {formatPercent(data.retention.retentionRate)}
                </p>
              </div>
              <div className="p-4 bg-red-50 rounded-lg border border-red-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.churnRate')}</p>
                <p className="text-xl font-bold text-red-600">
                  {formatPercent(data.retention.churnRate)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.oneTime')}</p>
                <p className="text-xl font-bold">{data.retention.oneTimeCustomers}</p>
              </div>
              <div className="p-4 bg-purple-50 rounded-lg border border-purple-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.repeat')}</p>
                <p className="text-xl font-bold text-purple-600">{data.retention.repeatCustomers}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Customer Lifetime Value */}
      {data.ltv && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5" />
              {t('analytics.customer.ltvDetails')}
            </CardTitle>
            <CardDescription>
              {t('analytics.customer.ltvDetailsDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.avgLTV')}</p>
                <p className="text-xl font-bold text-blue-600">
                  {formatCurrency(data.ltv.averageCustomerLTV)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.medianLTV')}</p>
                <p className="text-xl font-bold">{formatCurrency(data.ltv.medianCustomerLTV)}</p>
              </div>
              <div className="p-4 bg-green-50 rounded-lg border border-green-200">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.topTierLTV')}</p>
                <p className="text-xl font-bold text-green-600">
                  {formatCurrency(data.ltv.topTierCustomerLTV)}
                </p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.lowTierLTV')}</p>
                <p className="text-xl font-bold">{formatCurrency(data.ltv.lowTierCustomerLTV)}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.avgOrderValue')}</p>
                <p className="text-xl font-bold">{formatCurrency(data.ltv.averageOrderValue)}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.purchaseFrequency')}</p>
                <p className="text-xl font-bold">{data.ltv.averagePurchaseFrequency?.toFixed(2)}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.avgLifespan')}</p>
                <p className="text-xl font-bold">{Math.round(data.ltv.averageCustomerLifespanDays)} {t('common.days')}</p>
              </div>
              <div className="p-4 bg-gray-50 rounded-lg">
                <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.totalAnalyzed')}</p>
                <p className="text-xl font-bold">{data.ltv.totalCustomersAnalyzed}</p>
              </div>
            </div>

            <div className="mt-4 pt-4 border-t">
              <div className="flex justify-between items-center">
                <span className="text-sm font-medium text-gray-700">
                  {t('analytics.customer.totalCustomerValue')}:
                </span>
                <span className="text-2xl font-bold text-green-600">
                  {formatCurrency(data.ltv.totalCustomerValue)}
                </span>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Customer Satisfaction */}
      {data.satisfaction && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Star className="h-5 w-5" />
              {t('analytics.customer.satisfactionDetails')}
            </CardTitle>
            <CardDescription>
              {t('analytics.customer.satisfactionDetailsDesc')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-4">
                <h3 className="font-semibold text-sm text-gray-700">
                  {t('analytics.customer.ratingsBySource')}
                </h3>
                <div className="space-y-3">
                  <div className="flex justify-between items-center p-3 bg-red-50 rounded-lg">
                    <span className="text-sm font-medium">Google</span>
                    <div className="text-right">
                      <p className="font-bold text-red-600">
                        {data.satisfaction.googleRating?.toFixed(1) || '0.0'} ⭐
                      </p>
                      <p className="text-xs text-gray-600">{data.satisfaction.googleReviewCount} {t('analytics.customer.reviews')}</p>
                    </div>
                  </div>
                  <div className="flex justify-between items-center p-3 bg-blue-50 rounded-lg">
                    <span className="text-sm font-medium">Yandex</span>
                    <div className="text-right">
                      <p className="font-bold text-blue-600">
                        {data.satisfaction.yandexRating?.toFixed(1) || '0.0'} ⭐
                      </p>
                      <p className="text-xs text-gray-600">{data.satisfaction.yandexReviewCount} {t('analytics.customer.reviews')}</p>
                    </div>
                  </div>
                  <div className="flex justify-between items-center p-3 bg-sky-50 rounded-lg">
                    <span className="text-sm font-medium">Telegram</span>
                    <div className="text-right">
                      <p className="font-bold text-sky-600">
                        {data.satisfaction.telegramRating?.toFixed(1) || '0.0'} ⭐
                      </p>
                      <p className="text-xs text-gray-600">{data.satisfaction.telegramReviewCount} {t('analytics.customer.reviews')}</p>
                    </div>
                  </div>
                  <div className="flex justify-between items-center p-3 bg-purple-50 rounded-lg">
                    <span className="text-sm font-medium">{t('analytics.customer.internal')}</span>
                    <div className="text-right">
                      <p className="font-bold text-purple-600">
                        {data.satisfaction.internalRating?.toFixed(1) || '0.0'} ⭐
                      </p>
                      <p className="text-xs text-gray-600">{data.satisfaction.internalReviewCount} {t('analytics.customer.reviews')}</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="space-y-4">
                <h3 className="font-semibold text-sm text-gray-700">
                  {t('analytics.customer.overallMetrics')}
                </h3>
                <div className="space-y-3">
                  <div className="p-4 bg-yellow-50 rounded-lg border border-yellow-200">
                    <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.overallScore')}</p>
                    <p className="text-3xl font-bold text-yellow-600">
                      {data.satisfaction.overallSatisfactionScore?.toFixed(1) || '0.0'}
                    </p>
                  </div>
                  <div className="p-4 bg-gray-50 rounded-lg">
                    <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.avgRating')}</p>
                    <p className="text-2xl font-bold">
                      {data.satisfaction.averageRating?.toFixed(1) || '0.0'} ⭐
                    </p>
                  </div>
                  <div className="p-4 bg-gray-50 rounded-lg">
                    <p className="text-sm text-gray-600 mb-1">{t('analytics.customer.totalReviews')}</p>
                    <p className="text-2xl font-bold">{data.satisfaction.totalReviews}</p>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    <div className="p-3 bg-green-50 rounded-lg text-center">
                      <p className="text-xs text-gray-600 mb-1">{t('analytics.customer.positive')}</p>
                      <p className="font-bold text-green-600">{data.satisfaction.positiveReviews}</p>
                      <p className="text-xs text-gray-500">{formatPercent(data.satisfaction.positivePercentage)}</p>
                    </div>
                    <div className="p-3 bg-yellow-50 rounded-lg text-center">
                      <p className="text-xs text-gray-600 mb-1">{t('analytics.customer.neutral')}</p>
                      <p className="font-bold text-yellow-600">{data.satisfaction.neutralReviews}</p>
                    </div>
                    <div className="p-3 bg-red-50 rounded-lg text-center">
                      <p className="text-xs text-gray-600 mb-1">{t('analytics.customer.negative')}</p>
                      <p className="font-bold text-red-600">{data.satisfaction.negativeReviews}</p>
                      <p className="text-xs text-gray-500">{formatPercent(data.satisfaction.negativePercentage)}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
