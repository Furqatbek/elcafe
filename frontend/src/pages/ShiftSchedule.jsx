import { useState, useEffect } from 'react';
import { notifyError, notifySuccess, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { restaurantAPI, operatorAPI, waiterAPI } from '../services/api';
import { Card, CardContent } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import { ChevronLeft, ChevronRight, Plus, Copy, Layers, Pencil, Trash2 } from 'lucide-react';

const api = {
  getWeek: (restaurantId, weekStart) =>
    fetch(`/api/v1/shift-schedules/restaurant/${restaurantId}?weekStart=${weekStart}`, {
      headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` },
    }).then(r => r.json()),
  create: (data) =>
    fetch('/api/v1/shift-schedules', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('access_token')}` },
      body: JSON.stringify(data),
    }).then(r => r.json()),
  update: (id, data) =>
    fetch(`/api/v1/shift-schedules/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('access_token')}` },
      body: JSON.stringify(data),
    }).then(r => r.json()),
  delete: (id) =>
    fetch(`/api/v1/shift-schedules/${id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` },
    }).then(r => r.json()),
  copyWeek: (restaurantId, source, target) =>
    fetch(`/api/v1/shift-schedules/restaurant/${restaurantId}/copy-week?sourceWeek=${source}&targetWeek=${target}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` },
    }).then(r => r.json()),
  bulk: (data) =>
    fetch('/api/v1/shift-schedules/bulk', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('access_token')}` },
      body: JSON.stringify(data),
    }).then(r => r.json()),
};

function getMonday(date) {
  const d = new Date(date);
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  return new Date(d.setDate(diff)).toISOString().split('T')[0];
}

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const WEEKDAY_NUMS = [1, 2, 3, 4, 5, 6, 7]; // ISO: 1=Mon..7=Sun

export default function ShiftSchedule() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [employees, setEmployees] = useState([]);
  const [weekStart, setWeekStart] = useState(getMonday(new Date()));
  const [schedules, setSchedules] = useState([]);

  const [addOpen, setAddOpen] = useState(false);
  const [addForm, setAddForm] = useState({ employeeKey: '', shiftDate: '', startTime: '09:00', endTime: '17:00', role: '' });

  const [editOpen, setEditOpen] = useState(false);
  const [editForm, setEditForm] = useState({ id: null, shiftDate: '', startTime: '', endTime: '', role: '', notes: '' });

  const [bulkOpen, setBulkOpen] = useState(false);
  const todayStr = new Date().toISOString().split('T')[0];
  const monthAhead = (() => { const d = new Date(); d.setDate(d.getDate() + 30); return d.toISOString().split('T')[0]; })();
  const [bulkForm, setBulkForm] = useState({
    employeeKeys: [], weekdays: [1, 2, 3, 4, 5],
    fromDate: todayStr, toDate: monthAhead,
    startTime: '09:00', endTime: '17:00', role: '',
  });

  useEffect(() => {
    restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
      const list = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
      if (list.length > 0) setSelectedRestaurant(list[0].id.toString());
    }).catch(console.error);
    operatorAPI.getAll({ page: 0, size: 100 }).then(async (res) => {
      const operators = (res.data.data?.content || res.data.data || []).map(e => ({
        id: e.id, fullName: e.fullName, email: e.email, type: 'user', _role: 'Operator',
      }));
      let waiters = [];
      try {
        const wRes = await waiterAPI.getAll({ page: 0, size: 100 });
        waiters = (wRes.data.data?.content || wRes.data.data || []).map(w => ({
          id: w.id,
          fullName: w.name || w.fullName || `Waiter #${w.id}`,
          email: w.phone || '',
          type: 'waiter',
          _role: 'Waiter',
        }));
      } catch (e) { /* ignore */ }
      setEmployees([...operators, ...waiters]);
    }).catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedRestaurant) loadSchedule();
  }, [selectedRestaurant, weekStart]);

  const loadSchedule = async () => {
    try {
      const res = await api.getWeek(selectedRestaurant, weekStart);
      setSchedules(res.data || []);
    } catch (e) {
      console.error('Failed to load schedule:', e);
    }
  };

  const handleCreate = async () => {
    if (!addForm.employeeKey || !addForm.shiftDate) return;
    const [type, idStr] = addForm.employeeKey.split(':');
    const id = parseInt(idStr, 10);
    try {
      await api.create({
        restaurantId: parseInt(selectedRestaurant),
        employeeType: type,
        employeeId: type === 'user' ? id : null,
        waiterId: type === 'waiter' ? id : null,
        shiftDate: addForm.shiftDate,
        startTime: addForm.startTime,
        endTime: addForm.endTime,
        role: addForm.role || null,
      });
      setAddOpen(false);
      loadSchedule();
    } catch (e) {
      console.error('Failed to create schedule:', e);
      notifyError(e);
    }
  };

  const handleEditOpen = (s) => {
    setEditForm({
      id: s.id,
      shiftDate: s.shiftDate || '',
      startTime: (s.startTime || '09:00').slice(0, 5),
      endTime: (s.endTime || '17:00').slice(0, 5),
      role: s.role || '',
      notes: s.notes || '',
    });
    setEditOpen(true);
  };

  const handleEditSubmit = async () => {
    try {
      const res = await api.update(editForm.id, {
        shiftDate: editForm.shiftDate,
        startTime: editForm.startTime,
        endTime: editForm.endTime,
        role: editForm.role,
        notes: editForm.notes,
      });
      if (res?.success === false) {
        notifyWarning(res.message || 'Update failed');
        return;
      }
      setEditOpen(false);
      loadSchedule();
    } catch (e) {
      console.error('Failed to update schedule:', e);
      notifyError(e);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('shift.schedule.confirmDelete', 'Delete this shift?'))) return;
    await api.delete(id);
    loadSchedule();
  };

  const handleCopyWeek = async () => {
    const prev = new Date(weekStart);
    prev.setDate(prev.getDate() - 7);
    const prevStr = prev.toISOString().split('T')[0];
    await api.copyWeek(selectedRestaurant, prevStr, weekStart);
    loadSchedule();
  };

  const toggleBulkEmployee = (key) => {
    setBulkForm(f => ({
      ...f,
      employeeKeys: f.employeeKeys.includes(key)
        ? f.employeeKeys.filter(k => k !== key)
        : [...f.employeeKeys, key],
    }));
  };

  const toggleBulkWeekday = (n) => {
    setBulkForm(f => ({
      ...f,
      weekdays: f.weekdays.includes(n)
        ? f.weekdays.filter(d => d !== n)
        : [...f.weekdays, n].sort(),
    }));
  };

  const handleBulkSubmit = async () => {
    if (bulkForm.employeeKeys.length === 0 || bulkForm.weekdays.length === 0) return;
    const employees = bulkForm.employeeKeys.map(k => {
      const [type, id] = k.split(':');
      return { type, id: parseInt(id, 10) };
    });
    try {
      const res = await api.bulk({
        restaurantId: parseInt(selectedRestaurant),
        employees,
        weekdays: bulkForm.weekdays,
        fromDate: bulkForm.fromDate,
        toDate: bulkForm.toDate,
        startTime: bulkForm.startTime,
        endTime: bulkForm.endTime,
        role: bulkForm.role || null,
      });
      if (res?.success === false) {
        notifyWarning(res.message || 'Bulk create failed');
        return;
      }
      const created = res?.data?.created ?? 0;
      const skipped = res?.data?.skipped ?? 0;
      notifySuccess(t('shift.schedule.bulkResult',
        'Created {{created}} shifts, skipped {{skipped}} conflicts',
        { created, skipped }));
      setBulkOpen(false);
      loadSchedule();
    } catch (e) {
      console.error('Failed to bulk-create:', e);
      notifyError(e);
    }
  };

  const navigateWeek = (offset) => {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + offset * 7);
    setWeekStart(d.toISOString().split('T')[0]);
  };

  const subjectKey = (s) => s.waiter ? `waiter:${s.waiter.id}` : (s.employee ? `user:${s.employee.id}` : null);
  const subjectLabel = (s) => s.waiter
    ? `${s.waiter.name || `Waiter #${s.waiter.id}`}`
    : (s.employee?.fullName || s.employee?.email || '');
  const subjectKeys = [...new Set(schedules.map(subjectKey).filter(Boolean))];
  const subjectMap = {};
  schedules.forEach(s => { const k = subjectKey(s); if (k) subjectMap[k] = subjectLabel(s); });

  const getCell = (key, dayIdx) => {
    const date = new Date(weekStart);
    date.setDate(date.getDate() + dayIdx);
    const dateStr = date.toISOString().split('T')[0];
    return schedules.filter(s => subjectKey(s) === key && s.shiftDate === dateStr && s.status !== 'CANCELLED');
  };

  const formatWeekLabel = () => {
    const start = new Date(weekStart);
    const end = new Date(weekStart);
    end.setDate(end.getDate() + 6);
    return `${start.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} — ${end.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}`;
  };

  const weekdayLabel = (n) => t(`common.weekdaysShort.${n}`, DAYS[n - 1]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('shift.schedule.title', 'Shift Schedule')}</h1>
          <p className="text-muted-foreground">{t('shift.schedule.subtitle', 'Plan and manage employee shifts')}</p>
        </div>
        <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
          <SelectTrigger className="w-[200px]"><SelectValue /></SelectTrigger>
          <SelectContent>
            {restaurants.map(r => <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => navigateWeek(-1)}><ChevronLeft className="h-4 w-4" /></Button>
          <span className="text-lg font-semibold min-w-[220px] text-center">{formatWeekLabel()}</span>
          <Button variant="outline" size="icon" onClick={() => navigateWeek(1)}><ChevronRight className="h-4 w-4" /></Button>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleCopyWeek}><Copy className="h-4 w-4 mr-2" />{t('shift.schedule.copyWeek', 'Copy Prev Week')}</Button>
          <Button variant="outline" onClick={() => setBulkOpen(true)}><Layers className="h-4 w-4 mr-2" />{t('shift.schedule.bulkAdd', 'Bulk Add')}</Button>
          <Button onClick={() => setAddOpen(true)}><Plus className="h-4 w-4 mr-2" />{t('shift.schedule.addShift', 'Add Shift')}</Button>
        </div>
      </div>

      <Card>
        <CardContent className="p-0 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="text-left p-3 font-medium w-36">{t('shift.schedule.employee', 'Employee')}</th>
                {DAYS.map((day, idx) => {
                  const d = new Date(weekStart);
                  d.setDate(d.getDate() + idx);
                  return (
                    <th key={day} className="text-center p-3 font-medium">
                      <div>{day}</div>
                      <div className="text-xs text-muted-foreground">{d.getDate()}</div>
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {subjectKeys.length === 0 ? (
                <tr><td colSpan={8} className="text-center py-8 text-muted-foreground">{t('shift.schedule.noSchedules', 'No shifts scheduled this week')}</td></tr>
              ) : (
                subjectKeys.map(key => (
                  <tr key={key} className="border-b">
                    <td className="p-3 font-medium">{subjectMap[key] || key}</td>
                    {DAYS.map((_, dayIdx) => {
                      const cells = getCell(key, dayIdx);
                      return (
                        <td key={dayIdx} className="p-2 text-center">
                          {cells.length === 0 ? (
                            <span className="text-muted-foreground text-xs">—</span>
                          ) : (
                            <div className="flex flex-col gap-1 items-center">
                              {cells.map(s => (
                                <div key={s.id} className="inline-flex items-center gap-2 bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs font-medium">
                                  <span>{s.startTime?.slice(0, 5)}–{s.endTime?.slice(0, 5)}</span>
                                  <button
                                    onClick={() => handleEditOpen(s)}
                                    className="text-blue-700 hover:text-blue-900"
                                    title={t('common.edit', 'Edit')}
                                  >
                                    <Pencil className="h-3 w-3" />
                                  </button>
                                  <button
                                    onClick={() => handleDelete(s.id)}
                                    className="text-red-600 hover:text-red-800"
                                    title={t('common.delete', 'Delete')}
                                  >
                                    <Trash2 className="h-3 w-3" />
                                  </button>
                                </div>
                              ))}
                            </div>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>

      {/* Add Shift Dialog */}
      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('shift.schedule.addShift', 'Add Shift')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>{t('shift.schedule.employee', 'Employee')} *</Label>
              <select
                value={addForm.employeeKey}
                onChange={(e) => setAddForm({ ...addForm, employeeKey: e.target.value })}
                className="w-full border rounded-md px-3 py-2 text-sm bg-background"
              >
                <option value="">{t('shift.schedule.selectEmployee', 'Select employee')}</option>
                {employees.map(emp => (
                  <option key={`${emp.type}:${emp.id}`} value={`${emp.type}:${emp.id}`}>
                    {emp.fullName || emp.email}{emp._role ? ` (${emp._role})` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label>{t('shift.schedule.date', 'Date')} *</Label>
              <Input type="date" value={addForm.shiftDate} onChange={(e) => setAddForm({ ...addForm, shiftDate: e.target.value })} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('shift.schedule.startTime', 'Start')}</Label>
                <Input type="time" value={addForm.startTime} onChange={(e) => setAddForm({ ...addForm, startTime: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('shift.schedule.endTime', 'End')}</Label>
                <Input type="time" value={addForm.endTime} onChange={(e) => setAddForm({ ...addForm, endTime: e.target.value })} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('shift.schedule.role', 'Role')}</Label>
              <Input value={addForm.role} onChange={(e) => setAddForm({ ...addForm, role: e.target.value })} placeholder="Cashier, Waiter, Cook..." />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button onClick={handleCreate} disabled={!addForm.employeeKey || !addForm.shiftDate}>{t('common.add', 'Add')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Shift Dialog */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('shift.schedule.editShift', 'Edit Shift')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>{t('shift.schedule.date', 'Date')} *</Label>
              <Input type="date" value={editForm.shiftDate} onChange={(e) => setEditForm({ ...editForm, shiftDate: e.target.value })} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('shift.schedule.startTime', 'Start')}</Label>
                <Input type="time" value={editForm.startTime} onChange={(e) => setEditForm({ ...editForm, startTime: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('shift.schedule.endTime', 'End')}</Label>
                <Input type="time" value={editForm.endTime} onChange={(e) => setEditForm({ ...editForm, endTime: e.target.value })} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('shift.schedule.role', 'Role')}</Label>
              <Input value={editForm.role} onChange={(e) => setEditForm({ ...editForm, role: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>{t('shift.schedule.notes', 'Notes')}</Label>
              <Input value={editForm.notes} onChange={(e) => setEditForm({ ...editForm, notes: e.target.value })} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button onClick={handleEditSubmit}>{t('common.save', 'Save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Bulk Add Dialog */}
      <Dialog open={bulkOpen} onOpenChange={setBulkOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('shift.schedule.bulkAdd', 'Bulk Add Shifts')}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>{t('shift.schedule.employees', 'Employees')} *</Label>
              <div className="border rounded-md p-2 max-h-40 overflow-y-auto">
                {employees.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t('shift.schedule.noEmployees', 'No employees loaded')}</p>
                ) : (
                  employees.map(emp => {
                    const key = `${emp.type}:${emp.id}`;
                    return (
                      <label key={key} className="flex items-center gap-2 py-1 cursor-pointer hover:bg-muted/30 px-1 rounded">
                        <input
                          type="checkbox"
                          checked={bulkForm.employeeKeys.includes(key)}
                          onChange={() => toggleBulkEmployee(key)}
                        />
                        <span className="text-sm">{emp.fullName || emp.email}{emp._role ? ` (${emp._role})` : ''}</span>
                      </label>
                    );
                  })
                )}
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('shift.schedule.weekdays', 'Weekdays')} *</Label>
              <div className="flex gap-2 flex-wrap">
                {WEEKDAY_NUMS.map(n => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => toggleBulkWeekday(n)}
                    className={`px-3 py-1 rounded-md text-sm border ${
                      bulkForm.weekdays.includes(n)
                        ? 'bg-primary text-primary-foreground border-primary'
                        : 'bg-background hover:bg-muted'
                    }`}
                  >
                    {weekdayLabel(n)}
                  </button>
                ))}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('shift.schedule.fromDate', 'From')} *</Label>
                <Input type="date" value={bulkForm.fromDate} onChange={e => setBulkForm({ ...bulkForm, fromDate: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('shift.schedule.toDate', 'To')} *</Label>
                <Input type="date" value={bulkForm.toDate} onChange={e => setBulkForm({ ...bulkForm, toDate: e.target.value })} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('shift.schedule.startTime', 'Start')} *</Label>
                <Input type="time" value={bulkForm.startTime} onChange={e => setBulkForm({ ...bulkForm, startTime: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('shift.schedule.endTime', 'End')} *</Label>
                <Input type="time" value={bulkForm.endTime} onChange={e => setBulkForm({ ...bulkForm, endTime: e.target.value })} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('shift.schedule.role', 'Role')}</Label>
              <Input value={bulkForm.role} onChange={e => setBulkForm({ ...bulkForm, role: e.target.value })} />
            </div>
            <p className="text-xs text-muted-foreground">
              {t('shift.schedule.bulkHint',
                'Creates one shift per selected weekday in the date range, for every employee selected. Conflicts are skipped.')}
            </p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setBulkOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button
              onClick={handleBulkSubmit}
              disabled={bulkForm.employeeKeys.length === 0 || bulkForm.weekdays.length === 0}
            >
              {t('shift.schedule.bulkCreate', 'Create All')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
