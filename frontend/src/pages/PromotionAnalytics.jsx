import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { promotionAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import {
  TrendingUp,
  TrendingDown,
  Ticket,
  Percent,
  Calendar,
  Tag,
  BarChart3,
  DollarSign,
  ShoppingCart,
  Award,
  Target,
} from 'lucide-react';

export default function PromotionAnalytics() {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [dateRange, setDateRange] = useState({
    startDate: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    endDate: new Date().toISOString().split('T')[0],
  });

  const [data, setData] = useState({
    discountAnalytics: null,
    promotionsPerformance: [],
    discountTrends: [],
    topCoupons: [],
  });

  useEffect(() => {
    loadAnalytics();
  }, []);

  const loadAnalytics = async () => {
    setLoading(true);
    try {
      const restaurantId = 1; // Default restaurant

      const [analyticsRes, performanceRes, trendsRes, couponsRes] = await Promise.all([
        promotionAPI.getDiscountAnalytics(restaurantId, dateRange.startDate, dateRange.endDate),
        promotionAPI.getAllPromotionsPerformance(restaurantId),
        promotionAPI.getDiscountTrends(restaurantId, dateRange.startDate, dateRange.endDate),
        promotionAPI.getTopCoupons(restaurantId, dateRange.startDate, dateRange.endDate, 10),
      ]);

      setData({
        discountAnalytics: analyticsRes.data || null,
        promotionsPerformance: performanceRes.data || [],
        discountTrends: trendsRes.data || [],
        topCoupons: couponsRes.data || [],
      });
    } catch (error) {
      console.error('Failed to load promotion analytics:', error);
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
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(amount || 0);
  };

  const formatPercent = (percent) => {
    return `${(percent || 0).toFixed(2)}%`;
  };

  const analytics = data.discountAnalytics;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('analytics.promotion.title', 'Promotion Analytics')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('analytics.promotion.subtitle', 'Track discount performance, ROI, and coupon effectiveness')}
          </p>
        </div>
      </div>

      {/* Date Range Filter */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            {t('common.dateRange', 'Date Range')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex gap-4 items-end">
            <div className="flex-1">
              <label className="text-sm font-medium mb-2 block">
                {t('common.startDate', 'Start Date')}
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
                {t('common.endDate', 'End Date')}
              </label>
              <Input
                type="date"
                name="endDate"
                value={dateRange.endDate}
                onChange={handleDateChange}
              />
            </div>
            <Button onClick={handleApplyFilter} disabled={loading}>
              {loading ? t('common.loading', 'Loading...') : t('common.apply', 'Apply')}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Key Metrics */}
      {analytics && (
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t('analytics.promotion.totalDiscounts', 'Total Discounts')}
              </CardTitle>
              <Tag className="h-4 w-4 text-orange-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-orange-600">{formatCurrency(analytics.totalDiscountAmount)}</div>
              <p className="text-xs text-muted-foreground mt-1">
                {t('analytics.promotion.fromOrders', '{{count}} discounted orders', { count: analytics.totalDiscountedOrders || 0 })}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t('analytics.promotion.avgDiscount', 'Average Discount')}
              </CardTitle>
              <Percent className="h-4 w-4 text-blue-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{formatCurrency(analytics.averageDiscountPerOrder)}</div>
              <p className="text-xs text-muted-foreground mt-1">
                {t('analytics.promotion.perOrder', 'Per order')}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t('analytics.promotion.grossRevenue', 'Gross Revenue')}
              </CardTitle>
              <TrendingUp className="h-4 w-4 text-green-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">{formatCurrency(analytics.grossRevenue)}</div>
              <p className="text-xs text-muted-foreground mt-1">
                {t('analytics.promotion.beforeDiscounts', 'Before discounts')}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t('analytics.promotion.netRevenue', 'Net Revenue')}
              </CardTitle>
              <DollarSign className="h-4 w-4 text-purple-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{formatCurrency(analytics.netRevenue)}</div>
              <p className="text-xs text-muted-foreground mt-1">
                {t('analytics.promotion.afterDiscounts', 'After discounts')}
              </p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Discount Breakdown by Type */}
      {analytics?.discountsByType && Object.keys(analytics.discountsByType).length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Tag className="h-5 w-5" />
              {t('analytics.promotion.discountsByType', 'Discounts by Type')}
            </CardTitle>
            <CardDescription>
              {t('analytics.promotion.discountsByTypeDesc', 'Breakdown of discounts by discount type')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {Object.entries(analytics.discountsByType).map(([type, amount]) => (
                <div key={type} className="p-4 bg-gray-50 rounded-lg">
                  <p className="text-sm text-gray-600 mb-1">{type}</p>
                  <p className="text-xl font-bold text-orange-600">{formatCurrency(amount)}</p>
                  <p className="text-xs text-gray-500 mt-1">
                    {formatPercent((amount / analytics.totalDiscountAmount) * 100)} {t('common.ofTotal', 'of total')}
                  </p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Discount Trends */}
      {data.discountTrends.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <BarChart3 className="h-5 w-5" />
              {t('analytics.promotion.discountTrends', 'Daily Discount Trends')}
            </CardTitle>
            <CardDescription>
              {t('analytics.promotion.discountTrendsDesc', 'Daily breakdown of discount amounts and order counts')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4 font-medium">{t('common.date', 'Date')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.totalOrders', 'Total Orders')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.discountedOrders', 'Discounted')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.discountAmount', 'Discount Amount')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.grossRevenue', 'Gross Revenue')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.netRevenue', 'Net Revenue')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.discountTrends.map((day, idx) => (
                    <tr key={idx} className="border-b hover:bg-gray-50">
                      <td className="py-3 px-4">{day.date}</td>
                      <td className="py-3 px-4 text-right">{day.totalOrders}</td>
                      <td className="py-3 px-4 text-right">
                        <span className="text-orange-600">{day.discountedOrders}</span>
                        <span className="text-gray-400 text-xs ml-1">
                          ({day.totalOrders > 0 ? formatPercent((day.discountedOrders / day.totalOrders) * 100) : '0%'})
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right text-orange-600 font-medium">
                        {formatCurrency(day.totalDiscountAmount)}
                      </td>
                      <td className="py-3 px-4 text-right">{formatCurrency(day.grossRevenue)}</td>
                      <td className="py-3 px-4 text-right text-green-600 font-medium">
                        {formatCurrency(day.netRevenue)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Top Coupons */}
      {data.topCoupons.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Ticket className="h-5 w-5" />
              {t('analytics.promotion.topCoupons', 'Top Performing Coupons')}
            </CardTitle>
            <CardDescription>
              {t('analytics.promotion.topCouponsDesc', 'Most used coupons ranked by revenue generated')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4 font-medium">{t('common.rank', 'Rank')}</th>
                    <th className="text-left py-3 px-4 font-medium">{t('analytics.promotion.couponCode', 'Coupon Code')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.usageCount', 'Uses')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.totalDiscount', 'Total Discount')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.revenueGenerated', 'Revenue Generated')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.roi', 'ROI')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.topCoupons.map((coupon, idx) => {
                    const roi = coupon.totalDiscountGiven > 0
                      ? ((coupon.revenueGenerated - coupon.totalDiscountGiven) / coupon.totalDiscountGiven) * 100
                      : 0;
                    return (
                      <tr key={idx} className="border-b hover:bg-gray-50">
                        <td className="py-3 px-4">
                          <span className={`inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-medium ${
                            idx === 0 ? 'bg-yellow-100 text-yellow-800' :
                            idx === 1 ? 'bg-gray-100 text-gray-800' :
                            idx === 2 ? 'bg-orange-100 text-orange-800' :
                            'bg-gray-50 text-gray-600'
                          }`}>
                            {idx + 1}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-mono font-medium">{coupon.couponCode}</td>
                        <td className="py-3 px-4 text-right">{coupon.usageCount}</td>
                        <td className="py-3 px-4 text-right text-orange-600">
                          {formatCurrency(coupon.totalDiscountGiven)}
                        </td>
                        <td className="py-3 px-4 text-right text-green-600 font-medium">
                          {formatCurrency(coupon.revenueGenerated)}
                        </td>
                        <td className="py-3 px-4 text-right">
                          <span className={`font-medium ${roi > 0 ? 'text-green-600' : 'text-red-600'}`}>
                            {roi > 0 ? '+' : ''}{formatPercent(roi)}
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
      )}

      {/* Promotions Performance */}
      {data.promotionsPerformance.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Target className="h-5 w-5" />
              {t('analytics.promotion.promotionPerformance', 'Promotion Performance')}
            </CardTitle>
            <CardDescription>
              {t('analytics.promotion.promotionPerformanceDesc', 'ROI and effectiveness metrics for each promotion')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4 font-medium">{t('common.promotion', 'Promotion')}</th>
                    <th className="text-left py-3 px-4 font-medium">{t('common.type', 'Type')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.ordersWithPromo', 'Orders')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.totalDiscount', 'Total Discount')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.revenueGenerated', 'Revenue')}</th>
                    <th className="text-right py-3 px-4 font-medium">{t('analytics.promotion.roi', 'ROI')}</th>
                    <th className="text-center py-3 px-4 font-medium">{t('common.status', 'Status')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.promotionsPerformance.map((promo, idx) => (
                    <tr key={idx} className="border-b hover:bg-gray-50">
                      <td className="py-3 px-4 font-medium">{promo.promotionName}</td>
                      <td className="py-3 px-4">
                        <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                          {promo.promotionType}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-right">{promo.ordersWithPromotion}</td>
                      <td className="py-3 px-4 text-right text-orange-600">
                        {formatCurrency(promo.totalDiscountGiven)}
                      </td>
                      <td className="py-3 px-4 text-right text-green-600 font-medium">
                        {formatCurrency(promo.revenueGenerated)}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <span className={`font-medium ${promo.roi > 0 ? 'text-green-600' : 'text-red-600'}`}>
                          {promo.roi > 0 ? '+' : ''}{formatPercent(promo.roi)}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-center">
                        <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${
                          promo.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                        }`}>
                          {promo.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Empty State */}
      {!loading && !analytics && data.discountTrends.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <Tag className="h-12 w-12 text-gray-300 mb-4" />
            <h3 className="text-lg font-medium text-gray-900 mb-1">
              {t('analytics.promotion.noData', 'No promotion data available')}
            </h3>
            <p className="text-gray-500 text-center max-w-md">
              {t('analytics.promotion.noDataDesc', 'There are no discounts or promotions recorded for the selected date range. Try adjusting the date range or creating some promotions.')}
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
