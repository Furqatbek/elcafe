import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  DollarSign, TrendingUp, TrendingDown, AlertTriangle, Star, Target,
  ArrowUp, ArrowDown, Minus, RefreshCw, Calculator, BarChart3,
  Lightbulb, CheckCircle, XCircle, Clock
} from 'lucide-react';
import { pricingAPI, restaurantAPI } from '@/services/api';

const PricingDashboard = () => {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(searchParams.get('restaurantId') || '');
  const [loading, setLoading] = useState(false);
  const [targetMargin, setTargetMargin] = useState(30);

  // Data states
  const [analytics, setAnalytics] = useState(null);
  const [recommendations, setRecommendations] = useState([]);
  const [profitability, setProfitability] = useState([]);

  // Fetch restaurants on mount
  useEffect(() => {
    const fetchRestaurants = async () => {
      try {
        const response = await restaurantAPI.getAll();
        setRestaurants(response.data.data?.content || response.data.data || []);
        if (!selectedRestaurant && response.data.data?.content?.length > 0) {
          setSelectedRestaurant(response.data.data.content[0].id.toString());
        }
      } catch (error) {
        console.error('Error fetching restaurants:', error);
      }
    };
    fetchRestaurants();
  }, []);

  // Fetch data when restaurant changes
  useEffect(() => {
    if (selectedRestaurant) {
      fetchPricingData();
    }
  }, [selectedRestaurant]);

  const fetchPricingData = async () => {
    if (!selectedRestaurant) return;

    setLoading(true);
    try {
      const [analyticsRes, recsRes, profitRes] = await Promise.all([
        pricingAPI.getAnalytics(selectedRestaurant, { targetMargin }),
        pricingAPI.getRecommendations(selectedRestaurant, targetMargin),
        pricingAPI.getProfitability(selectedRestaurant)
      ]);

      setAnalytics(analyticsRes.data.data);
      setRecommendations(recsRes.data.data || []);
      setProfitability(profitRes.data.data || []);
    } catch (error) {
      console.error('Error fetching pricing data:', error);
    } finally {
      setLoading(false);
    }
  };

  const getMenuClassColor = (menuClass) => {
    switch (menuClass) {
      case 'STAR': return 'bg-yellow-100 text-yellow-800';
      case 'PLOW_HORSE': return 'bg-blue-100 text-blue-800';
      case 'PUZZLE': return 'bg-purple-100 text-purple-800';
      case 'DOG': return 'bg-gray-100 text-gray-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  const getMenuClassIcon = (menuClass) => {
    switch (menuClass) {
      case 'STAR': return <Star className="h-4 w-4" />;
      case 'PLOW_HORSE': return <TrendingUp className="h-4 w-4" />;
      case 'PUZZLE': return <Lightbulb className="h-4 w-4" />;
      case 'DOG': return <AlertTriangle className="h-4 w-4" />;
      default: return null;
    }
  };

  const getRecommendationIcon = (type) => {
    switch (type) {
      case 'INCREASE': return <ArrowUp className="h-4 w-4 text-green-600" />;
      case 'DECREASE': return <ArrowDown className="h-4 w-4 text-red-600" />;
      case 'MAINTAIN': return <Minus className="h-4 w-4 text-gray-600" />;
      case 'REVIEW': return <Clock className="h-4 w-4 text-orange-600" />;
      default: return null;
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'EXCELLENT': return 'bg-green-100 text-green-800';
      case 'HEALTHY': return 'bg-emerald-100 text-emerald-800';
      case 'ACCEPTABLE': return 'bg-yellow-100 text-yellow-800';
      case 'NEEDS_ATTENTION': return 'bg-orange-100 text-orange-800';
      case 'CRITICAL': return 'bg-red-100 text-red-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  const formatCurrency = (value) => {
    if (value == null) return '-';
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  };

  const formatPercentage = (value) => {
    if (value == null) return '-';
    return `${parseFloat(value).toFixed(1)}%`;
  };

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('pricing.title', 'Price Engineering')}</h1>
          <p className="text-muted-foreground">{t('pricing.subtitle', 'Analyze profitability and optimize pricing strategies')}</p>
        </div>
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2">
            <Label htmlFor="target-margin">{t('pricing.targetMargin', 'Target Margin')}:</Label>
            <Input
              id="target-margin"
              type="number"
              value={targetMargin}
              onChange={(e) => setTargetMargin(parseFloat(e.target.value) || 30)}
              className="w-20"
              min="0"
              max="100"
            />
            <span className="text-muted-foreground">%</span>
          </div>
          <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('common.selectRestaurant', 'Select restaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map((r) => (
                <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button onClick={fetchPricingData} disabled={loading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
            {t('common.refresh', 'Refresh')}
          </Button>
        </div>
      </div>

      {/* Summary Cards */}
      {analytics && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('pricing.averageMargin', 'Average Margin')}</CardTitle>
              <Target className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{formatPercentage(analytics.averageMarginPercentage)}</div>
              <p className="text-xs text-muted-foreground">
                {t('pricing.target', 'Target')}: {formatPercentage(analytics.targetMarginPercentage)}
                {analytics.marginGap > 0 && (
                  <span className="text-red-600 ml-1">({formatPercentage(analytics.marginGap)} {t('pricing.gap', 'gap')})</span>
                )}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('pricing.totalProfit30d', 'Total Profit (30d)')}</CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">{formatCurrency(analytics.totalProfit)}</div>
              <p className="text-xs text-muted-foreground">
                {t('pricing.revenue', 'Revenue')}: {formatCurrency(analytics.totalRevenue)}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('pricing.productsAnalyzed', 'Products Analyzed')}</CardTitle>
              <BarChart3 className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{analytics.productsWithCostData} / {analytics.totalProducts}</div>
              <p className="text-xs text-muted-foreground">
                {analytics.productsNeedingReview} {t('pricing.needReview', 'need review')}
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('pricing.recommendations', 'Recommendations')}</CardTitle>
              <Lightbulb className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{analytics.totalRecommendations}</div>
              <div className="flex gap-2 text-xs">
                <span className="text-green-600">↑ {analytics.increaseRecommendations}</span>
                <span className="text-red-600">↓ {analytics.decreaseRecommendations}</span>
                <span className="text-gray-600">= {analytics.maintainRecommendations}</span>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Menu Engineering Summary */}
      {analytics?.menuEngineering && (
        <Card>
          <CardHeader>
            <CardTitle>{t('pricing.menuEngineeringMatrix', 'Menu Engineering Matrix')}</CardTitle>
            <CardDescription>{t('pricing.menuEngineeringDesc', 'Product classification based on profitability and popularity')}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div className="p-4 bg-yellow-50 rounded-lg border border-yellow-200">
                <div className="flex items-center gap-2 mb-2">
                  <Star className="h-5 w-5 text-yellow-600" />
                  <span className="font-semibold text-yellow-800">{t('pricing.stars', 'Stars')}</span>
                </div>
                <div className="text-3xl font-bold text-yellow-700">{analytics.menuEngineering.stars}</div>
                <p className="text-xs text-yellow-600">{t('pricing.highProfitHighSales', 'High profit, High sales')}</p>
              </div>

              <div className="p-4 bg-blue-50 rounded-lg border border-blue-200">
                <div className="flex items-center gap-2 mb-2">
                  <TrendingUp className="h-5 w-5 text-blue-600" />
                  <span className="font-semibold text-blue-800">{t('pricing.plowHorses', 'Plow Horses')}</span>
                </div>
                <div className="text-3xl font-bold text-blue-700">{analytics.menuEngineering.plowHorses}</div>
                <p className="text-xs text-blue-600">{t('pricing.lowProfitHighSales', 'Low profit, High sales')}</p>
              </div>

              <div className="p-4 bg-purple-50 rounded-lg border border-purple-200">
                <div className="flex items-center gap-2 mb-2">
                  <Lightbulb className="h-5 w-5 text-purple-600" />
                  <span className="font-semibold text-purple-800">{t('pricing.puzzles', 'Puzzles')}</span>
                </div>
                <div className="text-3xl font-bold text-purple-700">{analytics.menuEngineering.puzzles}</div>
                <p className="text-xs text-purple-600">{t('pricing.highProfitLowSales', 'High profit, Low sales')}</p>
              </div>

              <div className="p-4 bg-gray-50 rounded-lg border border-gray-200">
                <div className="flex items-center gap-2 mb-2">
                  <AlertTriangle className="h-5 w-5 text-gray-600" />
                  <span className="font-semibold text-gray-800">{t('pricing.dogs', 'Dogs')}</span>
                </div>
                <div className="text-3xl font-bold text-gray-700">{analytics.menuEngineering.dogs}</div>
                <p className="text-xs text-gray-600">{t('pricing.lowProfitLowSales', 'Low profit, Low sales')}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Tabs for detailed views */}
      <Tabs defaultValue="recommendations" className="space-y-4">
        <TabsList>
          <TabsTrigger value="recommendations">{t('pricing.pricingRecommendations', 'Pricing Recommendations')}</TabsTrigger>
          <TabsTrigger value="profitability">{t('pricing.productProfitability', 'Product Profitability')}</TabsTrigger>
          <TabsTrigger value="categories">{t('pricing.categoryAnalysis', 'Category Analysis')}</TabsTrigger>
        </TabsList>

        {/* Recommendations Tab */}
        <TabsContent value="recommendations">
          <Card>
            <CardHeader>
              <CardTitle>{t('pricing.pricingRecommendations', 'Pricing Recommendations')}</CardTitle>
              <CardDescription>{t('pricing.pricingRecommendationsDesc', 'AI-powered pricing suggestions based on cost-plus analysis')}</CardDescription>
            </CardHeader>
            <CardContent>
              {recommendations.length === 0 ? (
                <p className="text-muted-foreground text-center py-8">
                  {t('pricing.noRecommendations', 'No recommendations available. Ensure products have cost data.')}
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b text-left">
                        <th className="pb-3 font-medium">{t('pricing.product', 'Product')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.currentPrice', 'Current Price')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.currentMargin', 'Current Margin')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.recommended', 'Recommended')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.change', 'Change')}</th>
                        <th className="pb-3 font-medium text-center">{t('pricing.action', 'Action')}</th>
                        <th className="pb-3 font-medium">{t('pricing.rationale', 'Rationale')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {recommendations.map((rec) => (
                        <tr key={rec.productId} className="border-b hover:bg-muted/50">
                          <td className="py-3">
                            <div className="font-medium">{rec.productName}</div>
                            <div className="text-sm text-muted-foreground">{rec.categoryName}</div>
                          </td>
                          <td className="py-3 text-right">{formatCurrency(rec.currentPrice)}</td>
                          <td className="py-3 text-right">
                            <Badge variant="outline" className={rec.currentMarginPercentage >= 30 ? 'text-green-600' : rec.currentMarginPercentage >= 15 ? 'text-yellow-600' : 'text-red-600'}>
                              {formatPercentage(rec.currentMarginPercentage)}
                            </Badge>
                          </td>
                          <td className="py-3 text-right font-medium">{formatCurrency(rec.recommendedPrice)}</td>
                          <td className="py-3 text-right">
                            <div className={`flex items-center justify-end gap-1 ${rec.priceChange > 0 ? 'text-green-600' : rec.priceChange < 0 ? 'text-red-600' : 'text-gray-600'}`}>
                              {rec.priceChange > 0 ? <ArrowUp className="h-4 w-4" /> : rec.priceChange < 0 ? <ArrowDown className="h-4 w-4" /> : <Minus className="h-4 w-4" />}
                              {formatCurrency(Math.abs(rec.priceChange))}
                              <span className="text-xs">({formatPercentage(rec.priceChangePercentage)})</span>
                            </div>
                          </td>
                          <td className="py-3 text-center">
                            <Badge className={
                              rec.recommendationType === 'INCREASE' ? 'bg-green-100 text-green-800' :
                              rec.recommendationType === 'DECREASE' ? 'bg-red-100 text-red-800' :
                              rec.recommendationType === 'MAINTAIN' ? 'bg-gray-100 text-gray-800' :
                              'bg-orange-100 text-orange-800'
                            }>
                              {getRecommendationIcon(rec.recommendationType)}
                              <span className="ml-1">{rec.recommendationType}</span>
                            </Badge>
                          </td>
                          <td className="py-3 text-sm text-muted-foreground max-w-xs truncate" title={rec.rationale}>
                            {rec.rationale}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Profitability Tab */}
        <TabsContent value="profitability">
          <Card>
            <CardHeader>
              <CardTitle>{t('pricing.profitabilityAnalysis', 'Product Profitability Analysis')}</CardTitle>
              <CardDescription>{t('pricing.profitabilityDesc', 'Detailed profitability breakdown for each product')}</CardDescription>
            </CardHeader>
            <CardContent>
              {profitability.length === 0 ? (
                <p className="text-muted-foreground text-center py-8">
                  {t('pricing.noProfitabilityData', 'No profitability data available.')}
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead>
                      <tr className="border-b text-left">
                        <th className="pb-3 font-medium">{t('pricing.product', 'Product')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.price', 'Price')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.cost', 'Cost')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.margin', 'Margin')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.unitsSold', 'Units Sold')}</th>
                        <th className="pb-3 font-medium text-right">{t('pricing.totalProfit', 'Total Profit')}</th>
                        <th className="pb-3 font-medium text-center">{t('pricing.class', 'Class')}</th>
                        <th className="pb-3 font-medium text-center">{t('pricing.status', 'Status')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {profitability.map((item) => (
                        <tr key={item.productId} className="border-b hover:bg-muted/50">
                          <td className="py-3">
                            <div className="font-medium">{item.productName}</div>
                            <div className="text-sm text-muted-foreground">{item.categoryName}</div>
                          </td>
                          <td className="py-3 text-right">{formatCurrency(item.sellingPrice)}</td>
                          <td className="py-3 text-right">{formatCurrency(item.costPrice)}</td>
                          <td className="py-3 text-right">
                            <div className={item.grossMarginPercentage >= 30 ? 'text-green-600' : item.grossMarginPercentage >= 15 ? 'text-yellow-600' : 'text-red-600'}>
                              {formatPercentage(item.grossMarginPercentage)}
                            </div>
                          </td>
                          <td className="py-3 text-right">{item.unitsSold}</td>
                          <td className="py-3 text-right font-medium text-green-600">
                            {formatCurrency(item.totalProfit)}
                          </td>
                          <td className="py-3 text-center">
                            <Badge className={getMenuClassColor(item.menuClass)}>
                              {getMenuClassIcon(item.menuClass)}
                              <span className="ml-1">{item.menuClass?.replace('_', ' ')}</span>
                            </Badge>
                          </td>
                          <td className="py-3 text-center">
                            <Badge className={getStatusColor(item.status)}>
                              {item.status?.replace('_', ' ')}
                            </Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Categories Tab */}
        <TabsContent value="categories">
          <Card>
            <CardHeader>
              <CardTitle>{t('pricing.categoryLevelAnalysis', 'Category-Level Pricing Analysis')}</CardTitle>
              <CardDescription>{t('pricing.categoryLevelDesc', 'Profitability breakdown by product category')}</CardDescription>
            </CardHeader>
            <CardContent>
              {!analytics?.categoryPricing || analytics.categoryPricing.length === 0 ? (
                <p className="text-muted-foreground text-center py-8">
                  {t('pricing.noCategoryData', 'No category data available.')}
                </p>
              ) : (
                <div className="space-y-4">
                  {analytics.categoryPricing.map((cat) => (
                    <div key={cat.categoryId} className="p-4 border rounded-lg">
                      <div className="flex justify-between items-start mb-3">
                        <div>
                          <h4 className="font-semibold">{cat.categoryName}</h4>
                          <p className="text-sm text-muted-foreground">{cat.productCount} {t('pricing.products', 'products')}</p>
                        </div>
                        <Badge variant="outline">
                          {formatPercentage(cat.revenueShare)} {t('pricing.ofRevenue', 'of revenue')}
                        </Badge>
                      </div>
                      <div className="grid grid-cols-4 gap-4 text-sm">
                        <div>
                          <p className="text-muted-foreground">{t('pricing.avgPrice', 'Avg Price')}</p>
                          <p className="font-medium">{formatCurrency(cat.averagePrice)}</p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">{t('pricing.avgMargin', 'Avg Margin')}</p>
                          <p className={`font-medium ${cat.averageMargin >= 30 ? 'text-green-600' : cat.averageMargin >= 15 ? 'text-yellow-600' : 'text-red-600'}`}>
                            {formatPercentage(cat.averageMargin)}
                          </p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">{t('pricing.revenue', 'Revenue')}</p>
                          <p className="font-medium">{formatCurrency(cat.totalRevenue)}</p>
                        </div>
                        <div>
                          <p className="text-muted-foreground">{t('pricing.profit', 'Profit')}</p>
                          <p className="font-medium text-green-600">{formatCurrency(cat.totalProfit)}</p>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default PricingDashboard;
