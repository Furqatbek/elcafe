import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { FileText } from 'lucide-react';

export default function POSuggestions() {
  const { t } = useTranslation();

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">{t('poSuggestions.title', 'Purchase Order Suggestions')}</h1>
        <p className="text-muted-foreground mt-1">
          {t('poSuggestions.subtitle', 'Auto-generated purchase order recommendations based on stock levels')}
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5" />
            {t('poSuggestions.suggestedOrders', 'Suggested Orders')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-center py-8">
            {t('poSuggestions.noSuggestions', 'No purchase order suggestions at this time. All stock levels are adequate.')}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
