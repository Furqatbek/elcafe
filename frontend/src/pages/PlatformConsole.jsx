import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { platformAPI, billingAPI } from '../services/api';
import { useToast } from '../hooks/useToast';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select';
import { Server, Search, Ban, CheckCircle2, CalendarPlus } from 'lucide-react';

const PAGE_SIZE = 20;

/**
 * SUPER_ADMIN platform console: list every tenant with its subscription state and manage it
 * cross-tenant — change plan, extend expiry, suspend / reactivate. Backed by PlatformAdminController
 * (/api/v1/platform). The route is guarded to SUPER_ADMIN in App.jsx; the API is guarded server-side.
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
    billingAPI.getPlans().then((r) => setPlans(r.data?.data || [])).catch(() => {});
  }, []);

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

  const statusBadge = (tn) => {
    if (!tn.active) return <Badge variant="destructive">{t('platform.status.suspended', 'Suspended')}</Badge>;
    if (tn.readOnly) return <Badge variant="destructive">{t('platform.status.readOnly', 'Read-only')}</Badge>;
    if (tn.inGracePeriod) return <Badge variant="secondary">{t('platform.status.grace', 'Grace')}</Badge>;
    if (tn.isTrial) return <Badge>{t('platform.status.trial', 'Trial')}</Badge>;
    return <Badge>{t('platform.status.active', 'Active')}</Badge>;
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
                    <td className="py-3 pr-4">
                      <Select
                        value={tn.planCode || ''}
                        onValueChange={(v) => v !== tn.planCode && runAction(
                          tn.restaurantId,
                          () => platformAPI.changePlan(tn.restaurantId, { planCode: v }),
                          'platform.planChanged', 'Plan changed')}
                      >
                        <SelectTrigger className="w-32">
                          <SelectValue placeholder={tn.planName || '—'} />
                        </SelectTrigger>
                        <SelectContent>
                          {plans.map((p) => <SelectItem key={p.code} value={p.code}>{p.name}</SelectItem>)}
                        </SelectContent>
                      </Select>
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
    </div>
  );
}
