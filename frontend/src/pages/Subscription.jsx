import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { billingAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { usePlan } from '../hooks/usePlan';
import { useToast } from '../hooks/useToast';
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

/**
 * Subscription page (mini-phase A3). Every authenticated user sees their current plan + the
 * catalogue; ADMINs additionally get a plan picker that calls the admin set-plan endpoint. No module
 * gating here yet — that lands in A4.
 */
export default function Subscription() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const user = useAuthStore((s) => s.user);
  const loadPlan = useAuthStore((s) => s.loadPlan);
  const { planName, planCode, features, daysUntilExpiry, isTrial, inGracePeriod, isReadOnly } = usePlan();
  const isAdmin = user?.role === 'ADMIN';

  const [plans, setPlans] = useState([]);
  const [form, setForm] = useState({ restaurantId: '', planCode: '', planExpiresAt: '', isTrial: false });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    billingAPI.getPlans().then((r) => setPlans(r.data?.data || [])).catch(() => {});
    loadPlan?.();
  }, [loadPlan]);

  const statusBadge = () => {
    if (isReadOnly) return <Badge variant="destructive">{t('subscription.status.readOnly', 'Read-only (expired)')}</Badge>;
    if (inGracePeriod) return <Badge variant="secondary">{t('subscription.status.grace', 'Grace period')}</Badge>;
    if (isTrial) return <Badge>{t('subscription.status.trial', 'Trial')}</Badge>;
    return <Badge>{t('subscription.status.active', 'Active')}</Badge>;
  };

  const expiryText = () => {
    if (daysUntilExpiry == null) return t('subscription.noExpiry', 'No expiry');
    if (daysUntilExpiry < 0) {
      return t('subscription.expiredDaysAgo', 'Expired {{n}} day(s) ago', { n: Math.abs(daysUntilExpiry) });
    }
    return t('subscription.daysRemaining', '{{n}} day(s) remaining', { n: daysUntilExpiry });
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
              <div className="text-sm">
                {t('subscription.unlockedModules', 'Unlocked modules')}: {features.length}
              </div>
            </>
          ) : (
            <div className="text-sm text-muted-foreground">
              {t('subscription.noPlan', 'No plan information available')}
            </div>
          )}
          {!isAdmin && (
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
            {plans.map((p) => (
              <div key={p.code} className={`rounded-lg border p-4 ${p.code === planCode ? 'border-primary ring-1 ring-primary' : ''}`}>
                <div className="font-medium">{p.name}</div>
                <div className="text-sm text-muted-foreground">
                  {p.monthlyPrice ? `${p.monthlyPrice.toLocaleString()} UZS/${t('subscription.perMonth', 'mo')}` : t('subscription.free', 'Free')}
                </div>
                <div className="text-xs mt-1">
                  {(p.featureCodes?.length || 0)} {t('subscription.features', 'features')}
                </div>
              </div>
            ))}
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
