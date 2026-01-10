import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../components/ui/card';
import { Bell, AlertTriangle, TrendingDown, CheckCircle } from 'lucide-react';

export default function FinancialAlerts() {
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">{t('financialAlerts.title', 'Financial Alerts')}</h1>
        <p className="text-muted-foreground mt-1">
          {t('financialAlerts.subtitle', 'Monitor financial health and receive alerts')}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('financialAlerts.activeAlerts', 'Active Alerts')}</CardTitle>
            <Bell className="h-4 w-4 text-yellow-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">0</div>
            <p className="text-xs text-muted-foreground">{t('financialAlerts.requireAttention', 'Require attention')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('financialAlerts.critical', 'Critical')}</CardTitle>
            <AlertTriangle className="h-4 w-4 text-red-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-red-600">0</div>
            <p className="text-xs text-muted-foreground">{t('financialAlerts.urgentAction', 'Urgent action needed')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('financialAlerts.warnings', 'Warnings')}</CardTitle>
            <TrendingDown className="h-4 w-4 text-yellow-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-600">0</div>
            <p className="text-xs text-muted-foreground">{t('financialAlerts.reviewSoon', 'Review soon')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('financialAlerts.resolved', 'Resolved')}</CardTitle>
            <CheckCircle className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">0</div>
            <p className="text-xs text-muted-foreground">{t('financialAlerts.thisWeek', 'This week')}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('financialAlerts.alertsList', 'Alerts')}</CardTitle>
          <CardDescription>
            {t('financialAlerts.alertsListDesc', 'Financial alerts and notifications')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-center py-8">
            {t('financialAlerts.noAlerts', 'No financial alerts at this time. All metrics are within normal ranges.')}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
