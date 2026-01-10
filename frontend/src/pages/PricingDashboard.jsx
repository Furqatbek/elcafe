import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../components/ui/card';
import { DollarSign, TrendingUp, Percent, Calculator } from 'lucide-react';

export default function PricingDashboard() {
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">{t('pricing.title', 'Pricing Dashboard')}</h1>
        <p className="text-muted-foreground mt-1">
          {t('pricing.subtitle', 'Manage product pricing and profit margins')}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('pricing.avgMargin', 'Avg. Margin')}</CardTitle>
            <Percent className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">--%</div>
            <p className="text-xs text-muted-foreground">{t('pricing.acrossAllProducts', 'Across all products')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('pricing.totalProducts', 'Total Products')}</CardTitle>
            <DollarSign className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">--</div>
            <p className="text-xs text-muted-foreground">{t('pricing.withPricing', 'With pricing set')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('pricing.belowTarget', 'Below Target')}</CardTitle>
            <TrendingUp className="h-4 w-4 text-red-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">--</div>
            <p className="text-xs text-muted-foreground">{t('pricing.needsReview', 'Needs pricing review')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('pricing.foodCost', 'Food Cost %')}</CardTitle>
            <Calculator className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">--%</div>
            <p className="text-xs text-muted-foreground">{t('pricing.targetBelow', 'Target: below 30%')}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('pricing.productPricing', 'Product Pricing')}</CardTitle>
          <CardDescription>
            {t('pricing.productPricingDesc', 'Review and adjust product prices and margins')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-center py-8">
            {t('pricing.comingSoon', 'Pricing analysis coming soon. Set up product costs to see margin calculations.')}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
