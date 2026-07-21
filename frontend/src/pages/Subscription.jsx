import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { billingAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { usePlan } from '../hooks/usePlan';
import { useToast } from '../hooks/useToast';
import { humanizeFeatureCode } from '../config/planFeatures';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Checkbox } from '../components/ui/checkbox';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select';
import { CreditCard } from 'lucide-react';

const DATE_LOCALES = { en: 'en-US', ru: 'ru-RU', uz: 'uz-UZ' };

/**
 * Subscription page (mini-phase A3). Every authenticated user sees their current plan + the
 * catalogue with the features each tier unlocks. OWNER/ADMIN can self-serve switch their OWN plan
 * (a free DB write — no payment phase yet); ADMINs additionally keep the cross-restaurant plan
 * picker that calls the admin set-plan endpoint.
 */
export default function Subscription() {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const user = useAuthStore((s) => s.user);
  const loadPlan = useAuthStore((s) => s.loadPlan);
  const {
    planName, planCode, features, daysUntilExpiry, planExpiresAt, restaurantId,
    isTrial, inGracePeriod, isReadOnly,
  } = usePlan();
  const isAdmin = user?.role === 'ADMIN';
  // Self-serve switch is for a tenant managing its OWN plan; the operator-only cross-restaurant tool
  // below stays ADMIN-gated separately.
  const canSelfManage = user?.role === 'OWNER' || user?.role === 'ADMIN';

  const [plans, setPlans] = useState([]);
  const [form, setForm] = useState({ restaurantId: '', planCode: '', planExpiresAt: '', isTrial: false });
  const [saving, setSaving] = useState(false);
  const [switchingCode, setSwitchingCode] = useState(null);

  const refreshPlans = () => billingAPI.getPlans().then((r) => setPlans(r.data?.data || [])).catch(() => {});

  useEffect(() => {
    refreshPlans();
    loadPlan?.();
  }, [loadPlan]);

  // code -> localized label, falling back to the JS humanizer for anything not in featureLabels.
  const featureLabels = t('subscription.featureLabels', { returnObjects: true });
  const labelFor = (code) => {
    const mapped = featureLabels && typeof featureLabels === 'object' ? featureLabels[code] : null;
    return mapped || humanizeFeatureCode(code);
  };

  const currentPlanMeta = plans.find((p) => p.code === planCode);

  const priceText = (p) => (
    p?.monthlyPrice
      ? `${p.monthlyPrice.toLocaleString()} UZS/${t('subscription.perMonth', 'mo')}`
      : t('subscription.free', 'Free')
  );

  const formatDate = (iso) => {
    if (!iso) return null;
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return null;
    return d.toLocaleDateString(DATE_LOCALES[i18n.language] || 'en-US', {
      day: 'numeric', month: 'short', year: 'numeric',
    });
  };

  const statusBadge = () => {
    if (isReadOnly) return <Badge variant="destructive">{t('subscription.status.readOnly', 'Read-only (expired)')}</Badge>;
    if (inGracePeriod) return <Badge variant="secondary">{t('subscription.status.grace', 'Grace period')}</Badge>;
    if (isTrial) return <Badge>{t('subscription.status.trial', 'Trial')}</Badge>;
    return <Badge>{t('subscription.status.active', 'Active')}</Badge>;
  };

  // Absolute expiry date joined with the relative countdown, e.g. "Expires 12 Aug 2026 · 30 day(s)
  // remaining". Null expiry => "No expiry".
  const expiryText = () => {
    const formatted = formatDate(planExpiresAt);
    if (!formatted) return t('subscription.noExpiry', 'No expiry');
    const absolute = t('subscription.expiresOn', 'Expires {{date}}', { date: formatted });
    let relative = null;
    if (daysUntilExpiry != null) {
      relative = daysUntilExpiry < 0
        ? t('subscription.expiredDaysAgo', 'Expired {{n}} day(s) ago', { n: Math.abs(daysUntilExpiry) })
        : t('subscription.daysRemaining', '{{n}} day(s) remaining', { n: daysUntilExpiry });
    }
    return relative ? `${absolute} · ${relative}` : absolute;
  };

  // Upgrade vs downgrade purely for the button label; the action is the same set-plan write.
  const actionLabel = (p) => {
    const cur = currentPlanMeta?.sortOrder;
    if (cur != null && p.sortOrder != null) {
      if (p.sortOrder > cur) return t('subscription.upgradeTo', 'Upgrade');
      if (p.sortOrder < cur) return t('subscription.downgradeTo', 'Downgrade');
    }
    return t('subscription.switchTo', 'Switch to this plan');
  };

  // Self-serve plan switch for the caller's OWN restaurant. restaurantId comes from the billing
  // status (never user input); expiry is always null (free/indefinite in the no-payment phase).
  const switchPlan = async (targetCode) => {
    if (!restaurantId) {
      toast({
        title: t('subscription.updateFailed', 'Could not update plan'),
        description: t('subscription.noRestaurant', 'No restaurant is linked to your account.'),
        variant: 'destructive',
      });
      return;
    }
    const targetName = plans.find((p) => p.code === targetCode)?.name || targetCode;
    if (!window.confirm(t(
      'subscription.switchConfirm',
      'Switch your plan to {{plan}}? Your available features change immediately.',
      { plan: targetName },
    ))) return;

    setSwitchingCode(targetCode);
    try {
      await billingAPI.adminSetPlan({
        restaurantId,
        planCode: targetCode,
        planExpiresAt: null,
        isTrial: false,
      });
      toast({ title: t('subscription.switched', 'Switched to {{plan}}', { plan: targetName }) });
      await loadPlan?.();
      await refreshPlans();
    } catch (err) {
      toast({
        title: t('subscription.updateFailed', 'Could not update plan'),
        description: err.app?.message || err.response?.data?.message,
        variant: 'destructive',
      });
    } finally {
      setSwitchingCode(null);
    }
  };

  const submitSetPlan = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      await billingAPI.adminSetPlan({
        restaurantId: Number(form.restaurantId),
        planCode: form.planCode,
        planExpiresAt: form.planExpiresAt ? new Date(form.planExpiresAt).toISOString() : null,
        isTrial: form.isTrial,
      });
      toast({ title: t('subscription.updated', 'Plan updated') });
      loadPlan?.();
    } catch (err) {
      toast({
        title: t('subscription.updateFailed', 'Could not update plan'),
        description: err.response?.data?.message,
        variant: 'destructive',
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-2">
        <CreditCard className="h-6 w-6" />
        <h1 className="text-2xl font-semibold">{t('subscription.title', 'Subscription')}</h1>
      </div>

      <Card>
        <CardHeader><CardTitle>{t('subscription.currentPlan', 'Current plan')}</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {planCode ? (
            <>
              <div className="flex items-center gap-3">
                <span className="text-xl font-medium">{planName || planCode}</span>
                {statusBadge()}
              </div>
              <div className="text-sm text-muted-foreground">{expiryText()}</div>
              {currentPlanMeta && (
                <div className="text-sm text-muted-foreground">
                  {t('subscription.price', 'Price')}: {priceText(currentPlanMeta)}
                </div>
              )}
              <div className="space-y-1.5 pt-1">
                <div className="text-sm font-medium">
                  {t('subscription.includedFeatures', 'Included features')}
                </div>
                {features.length > 0 ? (
                  <div className="flex flex-wrap gap-1.5">
                    {features.map((code) => (
                      <Badge key={code} variant="secondary">{labelFor(code)}</Badge>
                    ))}
                  </div>
                ) : (
                  <div className="text-sm text-muted-foreground">
                    {t('subscription.coreOnly', 'Core modules only')}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="text-sm text-muted-foreground">
              {t('subscription.noPlan', 'No plan information available')}
            </div>
          )}
          {!canSelfManage && (
            <div className="pt-2 text-sm">
              {t('subscription.contactUpgrade', 'Contact us to upgrade your plan.')}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle>{t('subscription.availablePlans', 'Available plans')}</CardTitle></CardHeader>
        <CardContent>
          <div className="grid gap-3 sm:grid-cols-3">
            {plans.map((p) => {
              const isCurrent = p.code === planCode;
              const codes = p.featureCodes || [];
              const shown = codes.slice(0, 4);
              const extra = codes.length - shown.length;
              return (
                <div key={p.code} className={`flex flex-col rounded-lg border p-4 ${isCurrent ? 'border-primary ring-1 ring-primary' : ''}`}>
                  <div className="font-medium">{p.name}</div>
                  <div className="text-sm text-muted-foreground">{priceText(p)}</div>
                  {codes.length > 0 ? (
                    <ul className="mt-2 space-y-1 text-xs text-muted-foreground">
                      {shown.map((code) => (
                        <li key={code}>· {labelFor(code)}</li>
                      ))}
                      {extra > 0 && (
                        <li className="font-medium">{t('subscription.andMore', 'and {{n}} more', { n: extra })}</li>
                      )}
                    </ul>
                  ) : (
                    <div className="mt-2 text-xs text-muted-foreground">{t('subscription.coreOnly', 'Core modules only')}</div>
                  )}
                  <div className="mt-4 pt-1">
                    {isCurrent ? (
                      <Button variant="outline" size="sm" disabled className="w-full">
                        {t('subscription.currentPlan', 'Current plan')}
                      </Button>
                    ) : canSelfManage ? (
                      <Button
                        size="sm"
                        className="w-full"
                        disabled={switchingCode != null}
                        onClick={() => switchPlan(p.code)}
                      >
                        {switchingCode === p.code
                          ? t('subscription.switching', 'Switching...')
                          : actionLabel(p)}
                      </Button>
                    ) : null}
                  </div>
                </div>
              );
            })}
            {plans.length === 0 && (
              <div className="text-sm text-muted-foreground">{t('subscription.noPlans', 'No plans available')}</div>
            )}
          </div>
        </CardContent>
      </Card>

      {isAdmin && (
        <Card>
          <CardHeader><CardTitle>{t('subscription.adminSetPlan', "Change a restaurant's plan")}</CardTitle></CardHeader>
          <CardContent>
            <form onSubmit={submitSetPlan} className="space-y-4 max-w-md">
              <div>
                <Label htmlFor="restaurantId">{t('subscription.restaurantId', 'Restaurant ID')}</Label>
                <Input
                  id="restaurantId"
                  type="number"
                  value={form.restaurantId}
                  onChange={(e) => setForm({ ...form, restaurantId: e.target.value })}
                  required
                />
              </div>
              <div>
                <Label>{t('subscription.plan', 'Plan')}</Label>
                <Select value={form.planCode} onValueChange={(v) => setForm({ ...form, planCode: v })}>
                  <SelectTrigger>
                    <SelectValue placeholder={t('subscription.selectPlan', 'Select a plan')} />
                  </SelectTrigger>
                  <SelectContent>
                    {plans.map((p) => <SelectItem key={p.code} value={p.code}>{p.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label htmlFor="planExpiresAt">{t('subscription.expiresAt', 'Expires at (optional)')}</Label>
                <Input
                  id="planExpiresAt"
                  type="datetime-local"
                  value={form.planExpiresAt}
                  onChange={(e) => setForm({ ...form, planExpiresAt: e.target.value })}
                />
              </div>
              <div className="flex items-center gap-2">
                <Checkbox
                  id="isTrial"
                  checked={form.isTrial}
                  onCheckedChange={(c) => setForm({ ...form, isTrial: !!c })}
                />
                <Label htmlFor="isTrial">{t('subscription.markTrial', 'Mark as trial')}</Label>
              </div>
              <Button type="submit" disabled={saving || !form.restaurantId || !form.planCode}>
                {saving ? t('common.saving', 'Saving...') : t('subscription.updatePlan', 'Update plan')}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
