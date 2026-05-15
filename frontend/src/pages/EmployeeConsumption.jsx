import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { restaurantAPI, menuAPI } from '../services/api';
import api from '../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '../components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table';
import { Plus, UtensilsCrossed } from 'lucide-react';

const consumptionAPI = {
  record: (restaurantId, data) => api.post(`/restaurants/${restaurantId}/employee-consumptions`, data),
  getByDate: (restaurantId, from, to) => api.get(`/restaurants/${restaurantId}/employee-consumptions`, { params: { from, to } }),
  usage: (restaurantId, from, to) => api.get(`/restaurants/${restaurantId}/employee-consumptions/usage`, { params: { from, to } }),
  previewAllowance: (restaurantId, params) =>
    api.get(`/restaurants/${restaurantId}/consumption-allowances/preview`, { params }),
};

function fmtMoney(n) {
  const v = Math.round(Number(n) || 0);
  return v.toLocaleString('en-US').replace(/,/g, ' ');
}

export default function EmployeeConsumption() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [consumptions, setConsumptions] = useState([]);
  const [usage, setUsage] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [products, setProducts] = useState([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dateRange, setDateRange] = useState({
    from: new Date().toISOString().split('T')[0],
    to: new Date().toISOString().split('T')[0],
  });
  const [form, setForm] = useState({ productId: '', quantity: '1', employeeKey: '', notes: '' });
  const [preview, setPreview] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  // Fetch the allowance preview whenever the dialog is open and the
  // form has enough data to evaluate. Debounce-ish via a short cleanup.
  useEffect(() => {
    if (!dialogOpen || !selectedRestaurant || !form.productId || !form.employeeKey) {
      setPreview(null);
      return undefined;
    }
    const [type, id] = form.employeeKey.split(':');
    const quantity = parseInt(form.quantity || '1', 10) || 1;
    let cancelled = false;
    setPreviewLoading(true);
    const handle = setTimeout(async () => {
      try {
        const params = {
          productId: parseInt(form.productId, 10),
          quantity,
        };
        if (type === 'user') params.employeeId = parseInt(id, 10);
        if (type === 'waiter') params.waiterId = parseInt(id, 10);
        const res = await consumptionAPI.previewAllowance(selectedRestaurant, params);
        if (!cancelled) setPreview(res.data?.data || null);
      } catch (e) {
        if (!cancelled) setPreview(null);
      } finally {
        if (!cancelled) setPreviewLoading(false);
      }
    }, 200);
    return () => { cancelled = true; clearTimeout(handle); };
  }, [dialogOpen, selectedRestaurant, form.productId, form.quantity, form.employeeKey]);

  useEffect(() => {
    restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
      const list = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
      if (list.length > 0) setSelectedRestaurant(list[0].id.toString());
    }).catch(console.error);

    api.get('/financial/payroll/employees').then(res => {
      setEmployees(res.data.data || []);
    }).catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadConsumptions();
      menuAPI.getProductsByRestaurant(selectedRestaurant).then(res => {
        const list = res.data.data?.content || res.data.data || [];
        setProducts(Array.isArray(list) ? list : []);
      }).catch(console.error);
    }
  }, [selectedRestaurant, dateRange]);

  const loadConsumptions = async () => {
    try {
      const [listRes, usageRes] = await Promise.all([
        consumptionAPI.getByDate(selectedRestaurant, dateRange.from, dateRange.to),
        consumptionAPI.usage(selectedRestaurant, dateRange.from, dateRange.to)
          .catch(() => ({ data: { data: [] } })),
      ]);
      setConsumptions(listRes.data.data || []);
      setUsage(usageRes.data?.data || []);
    } catch (e) { console.error(e); }
  };

  const handleRecord = async () => {
    try {
      const [type, id] = form.employeeKey.split(':');
      await consumptionAPI.record(selectedRestaurant, {
        productId: parseInt(form.productId),
        quantity: parseInt(form.quantity),
        waiterId: type === 'waiter' ? parseInt(id) : null,
        employeeId: type === 'user' ? parseInt(id) : null,
        notes: form.notes || null,
      });
      setDialogOpen(false);
      setForm({ productId: '', quantity: '1', employeeKey: '', notes: '' });
      loadConsumptions();
    } catch (e) {
      console.error(e);
      alert(e.response?.data?.message || 'Failed');
    }
  };

  const totalCost = consumptions.reduce((sum, c) => sum + (c.totalCost || 0), 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('consumption.title', 'Employee Consumption')}</h1>
          <p className="text-muted-foreground">{t('consumption.subtitle', 'Track what employees consume during shifts')}</p>
        </div>
        <div className="flex gap-2">
          <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
            <SelectTrigger className="w-[200px]"><SelectValue /></SelectTrigger>
            <SelectContent>
              {restaurants.map(r => <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>)}
            </SelectContent>
          </Select>
          <Button onClick={() => setDialogOpen(true)}>
            <Plus className="h-4 w-4 mr-2" /> {t('consumption.record', 'Record')}
          </Button>
        </div>
      </div>

      <div className="flex gap-4 items-end">
        <div className="space-y-1">
          <Label>{t('consumption.from', 'From')}</Label>
          <Input type="date" value={dateRange.from} onChange={e => setDateRange({ ...dateRange, from: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label>{t('consumption.to', 'To')}</Label>
          <Input type="date" value={dateRange.to} onChange={e => setDateRange({ ...dateRange, to: e.target.value })} />
        </div>
        <Badge variant="outline" className="h-9 px-4 text-base">
          {t('consumption.totalCost', 'Total')}: {totalCost.toLocaleString()}
        </Badge>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <UtensilsCrossed className="h-5 w-5" />
            {t('consumption.list', 'Consumption Log')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('consumption.time', 'Time')}</TableHead>
                <TableHead>{t('consumption.employee', 'Employee')}</TableHead>
                <TableHead>{t('consumption.product', 'Product')}</TableHead>
                <TableHead className="text-center">{t('consumption.qty', 'Qty')}</TableHead>
                <TableHead className="text-right">{t('consumption.cost', 'Cost')}</TableHead>
                <TableHead>{t('consumption.notes', 'Notes')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {consumptions.length === 0 ? (
                <TableRow><TableCell colSpan={6} className="text-center py-8 text-muted-foreground">{t('consumption.noRecords', 'No consumption records')}</TableCell></TableRow>
              ) : (
                consumptions.map(c => (
                  <TableRow key={c.id}>
                    <TableCell className="text-sm">{c.consumedAt ? new Date(c.consumedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}</TableCell>
                    <TableCell className="font-medium">{c.waiter?.name || ((c.employee?.firstName || '') + ' ' + (c.employee?.lastName || '')).trim() || '—'}</TableCell>
                    <TableCell>{c.productName}</TableCell>
                    <TableCell className="text-center">{c.quantity}</TableCell>
                    <TableCell className="text-right">{(c.totalCost || 0).toLocaleString()}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{c.notes || ''}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <UsageCard usage={usage} t={t} />

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>{t('consumption.recordTitle', 'Record Consumption')}</DialogTitle></DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="space-y-2">
              <Label>{t('consumption.employee', 'Employee')} *</Label>
              <select value={form.employeeKey} onChange={e => setForm({ ...form, employeeKey: e.target.value })} className="w-full border rounded-md px-3 py-2 text-sm bg-background">
                <option value="">{t('consumption.selectEmployee', 'Select employee')}</option>
                {employees.map(e => <option key={`${e.type}:${e.id}`} value={`${e.type}:${e.id}`}>{(e.fullName || '').trim() || e.email} ({e.role})</option>)}
              </select>
            </div>
            <div className="space-y-2">
              <Label>{t('consumption.product', 'Product')} *</Label>
              <select value={form.productId} onChange={e => setForm({ ...form, productId: e.target.value })} className="w-full border rounded-md px-3 py-2 text-sm bg-background">
                <option value="">{t('consumption.selectProduct', 'Select product')}</option>
                {products.map(p => <option key={p.id} value={p.id}>{p.name} {p.costPrice ? `(${p.costPrice.toLocaleString()})` : ''}</option>)}
              </select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('consumption.qty', 'Quantity')}</Label>
                <Input type="number" min="1" value={form.quantity} onChange={e => setForm({ ...form, quantity: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('consumption.notes', 'Notes')}</Label>
                <Input value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} placeholder={t('consumption.optional', 'Optional')} />
              </div>
            </div>

            <AllowancePreview preview={preview} loading={previewLoading} t={t} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleRecord} disabled={!form.employeeKey || !form.productId}>
              {preview?.overLimit
                ? (preview.willAutoCharge !== false
                    ? t('consumption.recordAndCharge', 'Record · charge {{amount}}', {
                        amount: fmtMoney(preview.chargedAmount),
                      })
                    : t('consumption.recordOverLimit', 'Record · over by {{amount}}', {
                        amount: fmtMoney(preview.chargedAmount),
                      }))
                : t('consumption.record', 'Record')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function UsageCard({ usage, t }) {
  if (!usage || usage.length === 0) return null;
  const totals = usage.reduce(
    (acc, u) => ({
      items: acc.items + (u.itemsCount || 0),
      cost: acc.cost + Number(u.totalCost || 0),
      charged: acc.charged + Number(u.chargedAmount || 0),
    }),
    { items: 0, cost: 0, charged: 0 }
  );
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('consumption.usage.title', 'Usage by consumer')}</CardTitle>
        <p className="text-sm text-muted-foreground">
          {t('consumption.usage.subtitle',
            '{{items}} items · {{cost}} so\'m total · {{charged}} so\'m charged back',
            {
              items: totals.items,
              cost: fmtMoney(totals.cost),
              charged: fmtMoney(totals.charged),
            })}
        </p>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('consumption.usage.col.name', 'Consumer')}</TableHead>
              <TableHead className="text-center">{t('consumption.usage.col.type', 'Type')}</TableHead>
              <TableHead className="text-right">{t('consumption.usage.col.items', 'Items')}</TableHead>
              <TableHead className="text-right">{t('consumption.usage.col.cost', 'Cost')}</TableHead>
              <TableHead className="text-right">{t('consumption.usage.col.charged', 'Charged')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {usage.map(u => (
              <TableRow key={`${u.subjectType}:${u.subjectId}`}>
                <TableCell className="font-medium">{u.name || '—'}</TableCell>
                <TableCell className="text-center">
                  <Badge variant="outline">
                    {t(`consumption.usage.type.${u.subjectType}`, u.subjectType)}
                  </Badge>
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {u.itemsCount}
                  {u.chargedItems > 0 && (
                    <span className="text-xs text-red-700 ml-1">
                      ({u.chargedItems} {t('consumption.usage.chargedShort', 'charged')})
                    </span>
                  )}
                </TableCell>
                <TableCell className="text-right tabular-nums">{fmtMoney(u.totalCost)}</TableCell>
                <TableCell className="text-right tabular-nums">
                  {Number(u.chargedAmount) > 0
                    ? <span className="text-red-700 font-semibold">{fmtMoney(u.chargedAmount)}</span>
                    : '—'}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function AllowancePreview({ preview, loading, t }) {
  if (loading) {
    return (
      <div className="text-xs text-muted-foreground italic">
        {t('consumption.allowance.checking', 'Checking allowance…')}
      </div>
    );
  }
  if (!preview) return null;
  if (!preview.hasAllowance) {
    return (
      <div className="text-xs text-muted-foreground">
        {t('consumption.allowance.none', 'No allowance configured — this consumption is on the house.')}
      </div>
    );
  }

  const remainCount = preview.remainingCount;
  const remainAmount = preview.remainingAmount;
  const periodLabel = t(`consumption.period.${preview.period}`, preview.period);

  if (preview.overLimit) {
    const autoCharge = preview.willAutoCharge !== false;
    const tone = autoCharge
      ? 'border-red-200 bg-red-50 text-red-800'
      : 'border-amber-200 bg-amber-50 text-amber-900';
    const subTone = autoCharge ? 'text-red-700' : 'text-amber-800';
    return (
      <div className={`rounded-md border p-3 text-sm ${tone}`}>
        <div className="font-semibold">
          {autoCharge
            ? t('consumption.allowance.overLimit',
                'Over allowance — {{amount}} will be charged to the employee',
                { amount: fmtMoney(preview.chargedAmount) })
            : t('consumption.allowance.overLimitManual',
                'Over allowance — {{amount}} is over the cap (manual settlement)',
                { amount: fmtMoney(preview.chargedAmount) })}
        </div>
        <div className={`mt-1 text-xs ${subTone}`}>
          {autoCharge
            ? t('consumption.allowance.willDeduct',
                'Will be deducted from the next salary as an advance.')
            : t('consumption.allowance.previewOnly',
                'Flagged on the consumption row; no salary advance will be posted automatically.')}
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm">
      <div className="font-medium text-emerald-800">
        {t('consumption.allowance.withinLimit', 'Within {{period}} allowance', { period: periodLabel })}
      </div>
      <div className="mt-1 grid grid-cols-2 gap-2 text-xs text-emerald-700">
        {preview.limitCount != null && (
          <div>
            {t('consumption.allowance.itemsLeft', '{{remain}} of {{limit}} items left', {
              remain: remainCount ?? 0,
              limit: preview.limitCount,
            })}
          </div>
        )}
        {preview.limitAmount != null && (
          <div>
            {t('consumption.allowance.amountLeft', '{{remain}} of {{limit}} left', {
              remain: fmtMoney(remainAmount ?? 0),
              limit: fmtMoney(preview.limitAmount),
            })}
          </div>
        )}
      </div>
    </div>
  );
}
