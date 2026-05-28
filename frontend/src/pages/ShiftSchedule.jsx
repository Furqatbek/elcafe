import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { restaurantAPI, operatorAPI, waiterAPI } from '../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
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
import { ChevronLeft, ChevronRight, Plus, Copy, Wand2, X } from 'lucide-react';

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
  autoFill: (restaurantId, weekStart) =>
    fetch(`/api/v1/shift-schedules/restaurant/${restaurantId}/auto-fill?weekStart=${weekStart}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` },
    }).then(r => r.json()),
};

function getMonday(date) {
  const d = new Date(date);
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  return new Date(d.setDate(diff)).toISOString().split('T')[0];
}

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export default function ShiftSchedule() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [employees, setEmployees] = useState([]);
  const [weekStart, setWeekStart] = useState(getMonday(new Date()));
  const [schedules, setSchedules] = useState([]);
  const [addOpen, setAddOpen] = useState(false);
  const [addForm, setAddForm] = useState({ employeeKey: '', shiftDate: '', startTime: '09:00', endTime: '17:00', role: '' });

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
      } catch (e) { /* ignore if waiter API fails */ }
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
      alert(e.message || 'Failed — possible conflict');
    }
  };

  const handleDelete = async (id) => {
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

  const handleAutoFill = async () => {
    await api.autoFill(selectedRestaurant, weekStart);
    loadSchedule();
  };

  const navigateWeek = (offset) => {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + offset * 7);
    setWeekStart(d.toISOString().split('T')[0]);
  };

  // Build grid data: unique subjects (user or waiter) × 7 days. A row key
  // is "user:<id>" or "waiter:<id>" so an operator and a waiter that
  // happen to share a numeric id don't collide.
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

      {/* Week navigation + actions */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => navigateWeek(-1)}><ChevronLeft className="h-4 w-4" /></Button>
          <span className="text-lg font-semibold min-w-[220px] text-center">{formatWeekLabel()}</span>
          <Button variant="outline" size="icon" onClick={() => navigateWeek(1)}><ChevronRight className="h-4 w-4" /></Button>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={handleCopyWeek}><Copy className="h-4 w-4 mr-2" />{t('shift.schedule.copyWeek', 'Copy Prev Week')}</Button>
          <Button variant="outline" onClick={handleAutoFill}><Wand2 className="h-4 w-4 mr-2" />{t('shift.schedule.autoFill', 'Auto-Fill')}</Button>
          <Button onClick={() => setAddOpen(true)}><Plus className="h-4 w-4 mr-2" />{t('shift.schedule.addShift', 'Add Shift')}</Button>
        </div>
      </div>

      {/* Schedule grid */}
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
                            cells.map(s => (
                              <div key={s.id} className="group relative inline-flex items-center gap-1 bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs font-medium">
                                {s.startTime?.slice(0, 5)}-{s.endTime?.slice(0, 5)}
                                <button
                                  onClick={() => handleDelete(s.id)}
                                  className="hidden group-hover:inline text-red-500 ml-1"
                                >
                                  <X className="h-3 w-3" />
                                </button>
                              </div>
                            ))
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
                <option value="">Select employee</option>
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
    </div>
  );
}
