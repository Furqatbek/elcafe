import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { platformAPI, billingAPI } from '../services/api';
import { useToast } from '../hooks/useToast';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Checkbox } from '../components/ui/checkbox';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from '../components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select';
import { Server, Search, Ban, CheckCircle2, CalendarPlus, XCircle, Pencil } from 'lucide-react';

const PAGE_SIZE = 20;

/**
 * SUPER_ADMIN platform console: list every tenant with its subscription lifecycle state and manage it
 * cross-tenant — change plan (with expiry + trial, so a plan change can't silently wipe them), extend
 * expiry, suspend / reactivate, cancel. Backed by PlatformAdminController (/api/v1/platform). The route
 * is guarded to SUPER_ADMIN in App.jsx; the API is guarded server-side.
 */
export default function PlatformConsole() {
  const { t } = useTranslation();
  const { toast } = useToast();

  const [tenants, setTenants] = useState([]);
  const [plans, setPlans] = useState([]);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState(null);
  // The tenant being plan-edited (dialog open when non-null) + the dialog's form state.
  const [planEdit, setPlanEdit] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    platformAPI.listTenants({ search: query || undefined, page, size: PAGE_SIZE })
      .then((r) => {
        // Page is serialized VIA_DTO → { content: [...], page: { totalPages, number, ... } }.
        const pg = r.data?.data || {};
        setTenants(pg.content || []);
        setTotalPages(pg.page?.totalPages ?? 1);
      })
      .catch(() => toast({ title: t('platform.loadFailed', 'Could not load tenants'), variant: 'destructive' }))
      .finally(() => setLoading(false));
  }, [query, page, toast, t]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    billingAPI.getPlans()
      .then((r) => setPlans(r.data?.data || []))
      .catch(() => toast({ title: t('platform.plansLoadFailed', 'Could not load the plan catalogue'), variant: 'destructive' }));
  }, [toast, t]);

  const runAction = async (id, fn, successKey, fallback) => {
    setBusyId(id);
    try {
      await fn();
      toast({ title: t(successKey, fallback) });
      load();
    } catch (err) {
      toast({
        title: t('platform.actionFailed', 'Action failed'),
        description: err.response?.data?.message,
        variant: 'destructive',
      });
    } finally {
      setBusyId(null);
    }
  };

  const applySearch = (e) => {
    e.preventDefault();
    setPage(0);
    setQuery(search.trim());
  };

  // Open the plan dialog prefilled with the tenant's current plan/expiry/trial, so applying without
  // touching a field preserves it — the backend writes all three unconditionally, so sending only the
  // plan code would silently null the expiry and clear the trial flag.
  const openPlanEdit = (tn) => setPlanEdit({
    tenant: tn,
    planCode: tn.planCode || '',
    // LocalDateTime "2026-07-30T12:00:00" → datetime-local input value "2026-07-30T12:00".
    planExpiresAt: tn.planExpiresAt ? tn.planExpiresAt.slice(0, 16) : '',
    isTrial: Boolean(tn.isTrial),
  });

  const submitPlanEdit = async (e) => {
    e.preventDefault();
    const { tenant: tn, planCode, planExpiresAt, isTrial } = planEdit;
    setPlanEdit(null);
    await runAction(
      tn.restaurantId,
      () => platformAPI.changePlan(tn.restaurantId, {
        planCode,
        planExpiresAt: planExpiresAt || null, // empty = no expiry (e.g. the free Start tier)
        isTrial,
      }),
      'platform.planChanged', 'Plan changed');
  };

  const cancelSubscription = (tn) => {
    // Sticky end-state (the daily reconcile never clears it; only Reactivate does) — confirm first.
    if (!window.confirm(t('platform.cancelConfirm',
      'Cancel this subscription? The tenant loses access and stays cancelled until explicitly reactivated.'))) {
      return;
    }
    runAction(tn.restaurantId, () => platformAPI.cancel(tn.restaurantId),
      'platform.cancelled', 'Subscription cancelled');
  };

  // Primary badge from the persisted lifecycle status (Phase 3); grace/read-only shown alongside
  // since they are plan-expiry facets an ACTIVE/TRIAL status doesn't carry.
  const statusBadge = (tn) => {
    const byStatus = {
      CANCELLED: <Badge variant="destructive">{t('platform.status.cancelled', 'Cancelled')}</Badge>,
      SUSPENDED: <Badge variant="destructive">{t('platform.status.suspended', 'Suspended')}</Badge>,
      EXPIRED: <Badge variant="destructive">{t('platform.status.expired', 'Expired')}</Badge>,
      PAST_DUE: <Badge variant="secondary">{t('platform.status.pastDue', 'Past due')}</Badge>,
      TRIAL: <Badge>{t('platform.status.trial', 'Trial')}</Badge>,
      ACTIVE: <Badge>{t('platform.status.active', 'Active')}</Badge>,
    };
    // Fallback derivation for a row without the lifecycle field (shouldn't happen post-V158).
    const primary = byStatus[tn.subscriptionStatus]
      || (!tn.active ? byStatus.SUSPENDED : tn.isTrial ? byStatus.TRIAL : byStatus.ACTIVE);
    return (
      <div className="flex flex-wrap gap-1">
        {primary}
        {tn.subscriptionStatus !== 'EXPIRED' && tn.readOnly
          && <Badge variant="destructive">{t('platform.status.readOnly', 'Read-only')}</Badge>}
        {tn.inGracePeriod && <Badge variant="secondary">{t('platform.status.grace', 'Grace')}</Badge>}
      </div>
    );
  };

  const expiryText = (tn) => {
    if (tn.daysUntilExpiry == null) return t('platform.noExpiry', 'No expiry');
    if (tn.daysUntilExpiry < 0) {
      return t('platform.expiredDaysAgo', 'Expired {{n}}d ago', { n: Math.abs(tn.daysUntilExpiry) });
    }
    return t('platform.daysLeft', '{{n}}d left', { n: tn.daysUntilExpiry });
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-2">
        <Server className="h-6 w-6" />
        <h1 className="text-2xl font-semibold">{t('platform.title', 'Platform console')}</h1>
      </div>

      <Card>
        <CardHeader><CardTitle>{t('platform.tenants', 'Tenants')}</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <form onSubmit={applySearch} className="flex gap-2 max-w-md">
            <Input
              placeholder={t('platform.searchPlaceholder', 'Search by name')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <Button type="submit" variant="outline" aria-label={t('platform.search', 'Search')}>
              <Search className="h-4 w-4" />
            </Button>
          </form>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-muted-foreground border-b">
                  <th className="py-2 pr-4 font-medium">{t('platform.col.name', 'Name')}</th>
                  <th className="py-2 pr-4 font-medium">{t('platform.col.plan', 'Plan')}</th>
                  <th className="py-2 pr-4 font-medium">{t('platform.col.status', 'Status')}</th>
                  <th className="py-2 pr-4 font-medium">{t('platform.col.expiry', 'Expiry')}</th>
                  <th className="py-2 pr-4 font-medium">{t('platform.col.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody>
                {tenants.map((tn) => (
                  <tr key={tn.restaurantId} className="border-b last:border-0 align-top">
                    <td className="py-3 pr-4">
                      <div className="font-medium">{tn.name}</div>
                      <div className="text-xs text-muted-foreground">#{tn.restaurantId}</div>
                    </td>
                    <td className="py-3 pr-4 whitespace-nowrap">
                      <Button
                        size="sm" variant="ghost" disabled={busyId === tn.restaurantId}
                        onClick={() => openPlanEdit(tn)}
                        aria-label={t('platform.changePlan', 'Change plan')}
                      >
                        {tn.planName || '—'}
                        <Pencil className="h-3 w-3 ml-2 text-muted-foreground" />
                      </Button>
                    </td>
                    <td className="py-3 pr-4">{statusBadge(tn)}</td>
                    <td className="py-3 pr-4 whitespace-nowrap">{expiryText(tn)}</td>
                    <td className="py-3 pr-4">
                      <div className="flex flex-wrap gap-2">
                        <Button
                          size="sm" variant="outline" disabled={busyId === tn.restaurantId}
                          onClick={() => runAction(tn.restaurantId, () => platformAPI.extend(tn.restaurantId, 30),
                            'platform.extended', 'Extended 30 days')}
                        >
                          <CalendarPlus className="h-4 w-4 mr-1" />{t('platform.extend30', '+30d')}
                        </Button>
                        {tn.active ? (
                          <Button
                            size="sm" variant="destructive" disabled={busyId === tn.restaurantId}
                            onClick={() => runAction(tn.restaurantId, () => platformAPI.suspend(tn.restaurantId),
                              'platform.suspended', 'Tenant suspended')}
                          >
                            <Ban className="h-4 w-4 mr-1" />{t('platform.suspend', 'Suspend')}
                          </Button>
                        ) : (
                          <Button
                            size="sm" variant="outline" disabled={busyId === tn.restaurantId}
                            onClick={() => runAction(tn.restaurantId, () => platformAPI.reactivate(tn.restaurantId),
                              'platform.reactivated', 'Tenant reactivated')}
                          >
                            <CheckCircle2 className="h-4 w-4 mr-1" />{t('platform.reactivate', 'Reactivate')}
                          </Button>
                        )}
                        {tn.subscriptionStatus !== 'CANCELLED' && (
                          <Button
                            size="sm" variant="outline" disabled={busyId === tn.restaurantId}
                            className="text-destructive"
                            onClick={() => cancelSubscription(tn)}
                          >
                            <XCircle className="h-4 w-4 mr-1" />{t('platform.cancel', 'Cancel')}
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                {!loading && tenants.length === 0 && (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-muted-foreground">
                      {t('platform.noTenants', 'No tenants found')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between">
            <Button variant="outline" size="sm" disabled={page <= 0 || loading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}>
              {t('common.previous', 'Previous')}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t('platform.pageOf', 'Page {{n}} of {{m}}', { n: page + 1, m: Math.max(1, totalPages) })}
            </span>
            <Button variant="outline" size="sm" disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage((p) => p + 1)}>
              {t('common.next', 'Next')}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Dialog open={planEdit != null} onOpenChange={(open) => !open && setPlanEdit(null)}>
        <DialogContent className="sm:max-w-md">
          {planEdit && (
            <form onSubmit={submitPlanEdit} className="space-y-4">
              <DialogHeader>
                <DialogTitle>
                  {t('platform.changePlanFor', 'Change plan — {{name}}', { name: planEdit.tenant.name })}
                </DialogTitle>
              </DialogHeader>
              <div className="space-y-2">
                <Label>{t('platform.plan', 'Plan')}</Label>
                <Select
                  value={planEdit.planCode}
                  onValueChange={(v) => setPlanEdit((s) => ({ ...s, planCode: v }))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t('platform.selectPlan', 'Select a plan')} />
                  </SelectTrigger>
                  <SelectContent>
                    {plans.map((p) => <SelectItem key={p.code} value={p.code}>{p.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="platform-plan-expiry">{t('platform.expiresAt', 'Expires at')}</Label>
                <Input
                  id="platform-plan-expiry" type="datetime-local"
                  value={planEdit.planExpiresAt}
                  onChange={(e) => setPlanEdit((s) => ({ ...s, planExpiresAt: e.target.value }))}
                />
                <p className="text-xs text-muted-foreground">
                  {t('platform.expiryHint', 'Leave empty for no expiry (e.g. the free Start tier).')}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Checkbox
                  id="platform-plan-trial"
                  checked={planEdit.isTrial}
                  onCheckedChange={(v) => setPlanEdit((s) => ({ ...s, isTrial: Boolean(v) }))}
                />
                <Label htmlFor="platform-plan-trial">{t('platform.markTrial', 'Trial assignment')}</Label>
              </div>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setPlanEdit(null)}>
                  {t('common.cancel', 'Cancel')}
                </Button>
                <Button type="submit" disabled={!planEdit.planCode}>
                  {t('platform.apply', 'Apply')}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
