import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Clock, AlertTriangle, Lock } from 'lucide-react';
import { usePlan } from '../hooks/usePlan';

// Keep in sync with PlanGateService.GRACE_DAYS on the backend.
const GRACE_DAYS = 3;

/**
 * Plan expiry / read-only banner (mini-phase A5). Shows from 7 days before expiry, through the grace
 * window, and into read-only — with trial-vs-paid wording. Renders nothing when there is no plan or
 * expiry is more than a week out, so it's invisible for restaurants without a (near-)expiring plan.
 */
export default function PlanExpiryBanner() {
  const { t } = useTranslation();
  const { planCode, daysUntilExpiry, isTrial, inGracePeriod, isReadOnly } = usePlan();

  if (!planCode) return null;
  const approaching = daysUntilExpiry != null && daysUntilExpiry <= 7;
  // A trial shows its countdown for the whole trial (e.g. "Trial ends in 14 days"), not just the
  // final week (mini-phase A7).
  const trialActive = isTrial && daysUntilExpiry != null && daysUntilExpiry > 0;
  if (!isReadOnly && !inGracePeriod && !approaching && !trialActive) return null;

  let tone;
  let Icon;
  let message;

  if (isReadOnly) {
    tone = 'bg-red-600 text-white';
    Icon = Lock;
    message = isTrial
      ? t('subscription.banner.readOnlyTrial', 'Your trial has ended. The app is read-only — renew to resume.')
      : t('subscription.banner.readOnly', 'Your plan has expired. The app is read-only — renew to resume.');
  } else if (inGracePeriod) {
    const graceLeft = Math.max(0, GRACE_DAYS + (daysUntilExpiry ?? 0));
    tone = 'bg-amber-500 text-white';
    Icon = AlertTriangle;
    message = t('subscription.banner.grace', 'Grace period — {{n}} day(s) left before the app becomes read-only.', { n: graceLeft });
  } else if (daysUntilExpiry <= 0) {
    tone = 'bg-amber-500 text-white';
    Icon = Clock;
    message = isTrial
      ? t('subscription.banner.trialEndsToday', 'Your trial ends today.')
      : t('subscription.banner.expiresToday', 'Your plan expires today.');
  } else {
    tone = daysUntilExpiry <= 3 ? 'bg-amber-500 text-white' : 'bg-blue-600 text-white';
    Icon = Clock;
    message = isTrial
      ? t('subscription.banner.trialEndsIn', 'Your trial ends in {{n}} day(s).', { n: daysUntilExpiry })
      : t('subscription.banner.expiresIn', 'Your plan expires in {{n}} day(s).', { n: daysUntilExpiry });
  }

  return (
    <div className={`flex items-center justify-center gap-3 px-4 py-2 text-sm font-medium ${tone}`}>
      <Icon className="h-4 w-4 shrink-0" />
      <span>{message}</span>
      <Link to="/subscription" className="underline underline-offset-2 hover:opacity-90 whitespace-nowrap">
        {t('subscription.banner.renew', 'Renew')}
      </Link>
    </div>
  );
}
