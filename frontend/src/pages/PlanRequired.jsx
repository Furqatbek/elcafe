import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Lock } from 'lucide-react';

/**
 * Shown when a user navigates (e.g. by direct URL) to a module their plan doesn't include
 * (mini-phase A4c). The sidebar already hides such modules; this is the fallback for direct access.
 */
export default function PlanRequired() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <Lock className="h-12 w-12 text-muted-foreground mb-4" />
      <h1 className="text-2xl font-semibold mb-2">
        {t('planRequired.title', 'Not included in your plan')}
      </h1>
      <p className="text-muted-foreground max-w-md mb-6">
        {t('planRequired.body', 'This module is part of a higher plan. Upgrade to unlock it.')}
      </p>
      <Link
        to="/subscription"
        className="inline-flex items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90"
      >
        {t('planRequired.cta', 'View plans')}
      </Link>
    </div>
  );
}
