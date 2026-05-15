import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import api, { restaurantAPI, menuAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '../components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table';
import { Plus, Trash2, Pencil } from 'lucide-react';

const allowanceAPI = {
  list: (restaurantId) => api.get(`/restaurants/${restaurantId}/consumption-allowances`),
  create: (restaurantId, data) => api.post(`/restaurants/${restaurantId}/consumption-allowances`, data),
  update: (restaurantId, id, data) => api.put(`/restaurants/${restaurantId}/consumption-allowances/${id}`, data),
  remove: (restaurantId, id) => api.delete(`/restaurants/${restaurantId}/consumption-allowances/${id}`),
};

const EMPTY_FORM = {
  id: null,
  subject: 'restaurant', // 'restaurant' | 'employee' | 'waiter'
  employeeId: '',
  waiterId: '',
  categoryId: '',
  period: 'DAILY',
  limitCount: '',
  limitAmount: '',
  notes: '',
  active: true,
};

function fmt(n) {
  if (n == null) return '—';
  return Math.round(Number(n) || 0).toLocaleString('en-US').replace(/,/g, ' ');
}

export default function ConsumptionAllowances() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [allowances, setAllowances] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [categories, setCategories] = useState([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  // Restaurants for the page-level switcher.
  useEffect(() => {
    restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
      const list = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
      if (list.length > 0) setSelectedRestaurant(list[0].id.toString());
    }).catch(console.error);
  }, []);

  const loadAllowances = useCallback(async () => {
    if (!selectedRestaurant) return;
    try {
      const res = await allowanceAPI.list(selectedRestaurant);
      setAllowances(res.data?.data || []);
    } catch (e) { console.error(e); }
  }, [selectedRestaurant]);

  useEffect(() => {
    if (!selectedRestaurant) return;
    loadAllowances();
    menuAPI.getCategories(selectedRestaurant).then(res => {
      setCategories(res.data?.data || []);
    }).catch(console.error);
    api.get('/financial/payroll/employees').then(res => {
      setEmployees(res.data?.data || []);
    }).catch(console.error);
  }, [selectedRestaurant, loadAllowances]);

  const openCreate = () => { setForm(EMPTY_FORM); setDialogOpen(true); };

  const openEdit = (a) => {
    setForm({
      id: a.id,
      subject: a.employee ? 'employee' : a.waiter ? 'waiter' : 'restaurant',
      employeeId: a.employee?.id?.toString() || '',
      waiterId: a.waiter?.id?.toString() || '',
      categoryId: a.category?.id?.toString() || '',
      period: a.period || 'DAILY',
      limitCount: a.limitCount?.toString() || '',
      limitAmount: a.limitAmount?.toString() || '',
      notes: a.notes || '',
      active: a.active !== false,
    });
    setDialogOpen(true);
  };

  const submit = async () => {
    try {
      const payload = {
        categoryId: form.categoryId ? parseInt(form.categoryId, 10) : null,
        employeeId: form.subject === 'employee' && form.employeeId
          ? parseInt(form.employeeId, 10) : null,
        waiterId: form.subject === 'waiter' && form.waiterId
          ? parseInt(form.waiterId, 10) : null,
        period: form.period,
        limitCount: form.limitCount ? parseInt(form.limitCount, 10) : null,
        limitAmount: form.limitAmount ? parseFloat(form.limitAmount) : null,
        notes: form.notes || null,
        active: form.active,
      };
      if (payload.limitCount == null && payload.limitAmount == null) {
        alert(t('allowances.errors.noCap', 'Set an item cap, a money cap, or both.'));
        return;
      }
      if (form.id) {
        await allowanceAPI.update(selectedRestaurant, form.id, payload);
      } else {
        await allowanceAPI.create(selectedRestaurant, payload);
      }
      setDialogOpen(false);
      loadAllowances();
    } catch (e) {
      console.error(e);
      alert(e.response?.data?.message || 'Failed');
    }
  };

  const remove = async (a) => {
    if (!window.confirm(t('allowances.confirmDelete', 'Delete this allowance?'))) return;
    try {
      await allowanceAPI.remove(selectedRestaurant, a.id);
      loadAllowances();
    } catch (e) {
      console.error(e);
      alert(e.response?.data?.message || 'Failed');
    }
  };

  const subjectLabel = (a) => {
    if (a.employee) {
      return `${a.employee.firstName || ''} ${a.employee.lastName || a.employee.email || ''}`.trim() || a.employee.email;
    }
    if (a.waiter) return a.waiter.name;
    return t('allowances.subject.restaurant', 'All employees');
  };

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold">{t('allowances.title', 'Consumption Allowances')}</h1>
          <p className="text-sm text-muted-foreground">
            {t('allowances.subtitle', 'Cap free employee consumption per day or shift. Overflow is auto-charged to the next salary.')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {restaurants.length > 1 && (
            <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
              <SelectTrigger className="w-[180px]"><SelectValue /></SelectTrigger>
              <SelectContent>
                {restaurants.map(r => <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>)}
              </SelectContent>
            </Select>
          )}
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4 mr-2" /> {t('allowances.add', 'Add allowance')}
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('allowances.listTitle', 'Configured allowances')}</CardTitle>
          <CardDescription>
            {t('allowances.listDesc', 'Most specific rule wins: employee/waiter > restaurant-wide; category-specific > any category.')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('allowances.col.subject', 'Subject')}</TableHead>
                <TableHead>{t('allowances.col.category', 'Category')}</TableHead>
                <TableHead className="text-center">{t('allowances.col.period', 'Period')}</TableHead>
                <TableHead className="text-right">{t('allowances.col.items', 'Items')}</TableHead>
                <TableHead className="text-right">{t('allowances.col.amount', 'Amount')}</TableHead>
                <TableHead className="text-center">{t('allowances.col.status', 'Status')}</TableHead>
                <TableHead className="text-right">{t('allowances.col.actions', 'Actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {allowances.length === 0 ? (
                <TableRow><TableCell colSpan={7} className="text-center text-muted-foreground py-8">
                  {t('allowances.empty', 'No allowances configured.')}
                </TableCell></TableRow>
              ) : allowances.map(a => (
                <TableRow key={a.id}>
                  <TableCell className="font-medium">{subjectLabel(a)}</TableCell>
                  <TableCell>{a.category?.name || t('allowances.anyCategory', 'Any category')}</TableCell>
                  <TableCell className="text-center">
                    <Badge variant="outline">{t(`consumption.period.${a.period}`, a.period)}</Badge>
                  </TableCell>
                  <TableCell className="text-right">{a.limitCount ?? '—'}</TableCell>
                  <TableCell className="text-right">{fmt(a.limitAmount)}</TableCell>
                  <TableCell className="text-center">
                    <Badge variant={a.active ? 'default' : 'secondary'}>
                      {a.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex gap-1 justify-end">
                      <Button variant="ghost" size="icon" onClick={() => openEdit(a)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => remove(a)}>
                        <Trash2 className="h-4 w-4 text-red-500" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>
              {form.id ? t('allowances.editTitle', 'Edit allowance') : t('allowances.addTitle', 'Add allowance')}
            </DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="space-y-2">
              <Label>{t('allowances.form.subject', 'Applies to')}</Label>
              <Select value={form.subject} onValueChange={v => setForm({ ...form, subject: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="restaurant">{t('allowances.subject.restaurant', 'All employees')}</SelectItem>
                  <SelectItem value="employee">{t('allowances.subject.employee', 'Specific employee')}</SelectItem>
                  <SelectItem value="waiter">{t('allowances.subject.waiter', 'Specific waiter')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {form.subject === 'employee' && (
              <div className="space-y-2">
                <Label>{t('allowances.form.employee', 'Employee')}</Label>
                <select
                  className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                  value={form.employeeId}
                  onChange={e => setForm({ ...form, employeeId: e.target.value })}
                >
                  <option value="">{t('allowances.form.pickEmployee', 'Pick employee…')}</option>
                  {employees.filter(e => e.type !== 'waiter').map(e => (
                    <option key={`u:${e.id}`} value={e.id}>
                      {(e.fullName || '').trim() || e.email} ({e.role})
                    </option>
                  ))}
                </select>
              </div>
            )}

            {form.subject === 'waiter' && (
              <div className="space-y-2">
                <Label>{t('allowances.form.waiter', 'Waiter')}</Label>
                <select
                  className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                  value={form.waiterId}
                  onChange={e => setForm({ ...form, waiterId: e.target.value })}
                >
                  <option value="">{t('allowances.form.pickWaiter', 'Pick waiter…')}</option>
                  {employees.filter(e => e.type === 'waiter').map(e => (
                    <option key={`w:${e.id}`} value={e.id}>
                      {e.fullName || e.email}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="space-y-2">
              <Label>{t('allowances.form.category', 'Category (optional)')}</Label>
              <select
                className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                value={form.categoryId}
                onChange={e => setForm({ ...form, categoryId: e.target.value })}
              >
                <option value="">{t('allowances.anyCategory', 'Any category')}</option>
                {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>

            <div className="space-y-2">
              <Label>{t('allowances.form.period', 'Period')}</Label>
              <Select value={form.period} onValueChange={v => setForm({ ...form, period: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="PER_SHIFT">{t('consumption.period.PER_SHIFT', 'Per shift')}</SelectItem>
                  <SelectItem value="DAILY">{t('consumption.period.DAILY', 'Daily')}</SelectItem>
                  <SelectItem value="WEEKLY">{t('consumption.period.WEEKLY', 'Weekly')}</SelectItem>
                  <SelectItem value="MONTHLY">{t('consumption.period.MONTHLY', 'Monthly')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>{t('allowances.form.limitCount', 'Items cap')}</Label>
                <Input type="number" min="0" value={form.limitCount}
                  onChange={e => setForm({ ...form, limitCount: e.target.value })}
                  placeholder={t('allowances.form.optional', 'Optional')} />
              </div>
              <div className="space-y-2">
                <Label>{t('allowances.form.limitAmount', 'Money cap')}</Label>
                <Input type="number" min="0" value={form.limitAmount}
                  onChange={e => setForm({ ...form, limitAmount: e.target.value })}
                  placeholder={t('allowances.form.optional', 'Optional')} />
              </div>
            </div>

            <div className="space-y-2">
              <Label>{t('allowances.form.notes', 'Notes')}</Label>
              <Input value={form.notes}
                onChange={e => setForm({ ...form, notes: e.target.value })}
                placeholder={t('allowances.form.optional', 'Optional')} />
            </div>

            <div className="flex items-center gap-2">
              <input id="active" type="checkbox"
                checked={form.active}
                onChange={e => setForm({ ...form, active: e.target.checked })} />
              <Label htmlFor="active">{t('common.active', 'Active')}</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={submit}>{t('common.save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
