import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { shiftAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table';
import {
  Clock,
  Coffee,
  AlertTriangle,
  CheckCircle,
  Send,
  Users,
  Timer,
  UserCheck,
  LogOut,
} from 'lucide-react';

const OVERTIME_THRESHOLD_HOURS = 8;
const POLL_INTERVAL = 30000; // 30 seconds

export default function ShiftDashboard() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [activeShifts, setActiveShifts] = useState([]);
  const [completedShifts, setCompletedShifts] = useState([]);
  const [now, setNow] = useState(Date.now());
  const today = new Date().toISOString().split('T')[0];
  const [dateRange, setDateRange] = useState({ from: today, to: today });

  useEffect(() => {
    restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
      const list = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
      if (list.length > 0) setSelectedRestaurant(list[0].id.toString());
    }).catch(console.error);
  }, []);

  const loadShifts = useCallback(async () => {
    if (!selectedRestaurant) return;
    try {
      const [activeRes, rangeRes] = await Promise.all([
        shiftAPI.getActive(selectedRestaurant),
        shiftAPI.getByDateRange(selectedRestaurant, dateRange.from, dateRange.to),
      ]);
      const active = activeRes.data.data || activeRes.data || [];
      const ranged = rangeRes.data.data || rangeRes.data || [];
      setActiveShifts(Array.isArray(active) ? active : []);
      setCompletedShifts(
        (Array.isArray(ranged) ? ranged : [])
          .filter(s => s.status === 'COMPLETED' || s.status === 'APPROVED')
      );
    } catch (e) {
      console.error('Failed to load shifts:', e);
    }
  }, [selectedRestaurant, dateRange.from, dateRange.to]);

  useEffect(() => {
    loadShifts();
    const interval = setInterval(loadShifts, POLL_INTERVAL);
    return () => clearInterval(interval);
  }, [loadShifts]);

  // Live timer tick
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);

  const formatDuration = (minutes) => {
    if (minutes == null) return '--';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return `${h}h ${m}m`;
  };

  const getLiveMinutes = (clockIn) => {
    if (!clockIn) return 0;
    return Math.floor((now - new Date(clockIn).getTime()) / 60000);
  };

  const isApproachingOvertime = (clockIn) => {
    return getLiveMinutes(clockIn) >= (OVERTIME_THRESHOLD_HOURS * 60 - 60); // warn 1h before
  };

  const isOvertime = (clockIn) => {
    return getLiveMinutes(clockIn) >= OVERTIME_THRESHOLD_HOURS * 60;
  };

  const handleClockOut = async (shiftId) => {
    try {
      await shiftAPI.clockOut(selectedRestaurant, shiftId, {});
      loadShifts();
    } catch (e) {
      console.error('Failed to clock out:', e);
    }
  };

  const handleStartBreak = async (shiftId) => {
    try {
      await shiftAPI.startBreak(selectedRestaurant, shiftId);
      loadShifts();
    } catch (e) {
      console.error('Failed to start break:', e);
    }
  };

  const handleEndBreak = async (shiftId) => {
    try {
      await shiftAPI.endBreak(selectedRestaurant, shiftId);
      loadShifts();
    } catch (e) {
      console.error('Failed to end break:', e);
    }
  };

  const handleApprove = async (shiftId) => {
    try {
      await shiftAPI.approve(selectedRestaurant, shiftId);
      loadShifts();
    } catch (e) {
      console.error('Failed to approve shift:', e);
    }
  };

  const handleResendTelegram = async (shiftId) => {
    try {
      await shiftAPI.resendTelegram(selectedRestaurant, shiftId);
      alert(t('shift.dashboard.telegramSent', 'Shift report sent to Telegram'));
    } catch (e) {
      console.error('Failed to resend:', e);
      alert('Failed to send');
    }
  };

  // Stats
  const activeCount = activeShifts.filter(s => s.status === 'ACTIVE').length;
  const onBreakCount = activeShifts.filter(s => s.status === 'ON_BREAK').length;
  const totalHours = activeShifts.reduce((sum, s) => sum + getLiveMinutes(s.clockIn), 0);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('shift.dashboard.title', 'Shift Dashboard')}</h1>
          <p className="text-muted-foreground">{t('shift.dashboard.subtitle', 'Real-time view of active shifts')}</p>
        </div>
        <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder={t('common.selectRestaurant', 'Select Restaurant')} />
          </SelectTrigger>
          <SelectContent>
            {restaurants.map(r => (
              <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('shift.dashboard.activeNow', 'Active Now')}</CardTitle>
            <Users className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-green-600">{activeCount}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('shift.dashboard.onBreak', 'On Break')}</CardTitle>
            <Coffee className="h-4 w-4 text-amber-500" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-amber-600">{onBreakCount}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('shift.dashboard.totalHours', 'Total Hours Today')}</CardTitle>
            <Timer className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold">{formatDuration(totalHours)}</div>
          </CardContent>
        </Card>
      </div>

      {/* Active Shifts */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <UserCheck className="h-5 w-5" />
            {t('shift.dashboard.activeShifts', 'Active Shifts')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {activeShifts.length === 0 ? (
            <p className="text-center text-muted-foreground py-8">{t('shift.dashboard.noActive', 'No active shifts')}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('shift.dashboard.employee', 'Employee')}</TableHead>
                  <TableHead>{t('shift.dashboard.clockedIn', 'Clocked In')}</TableHead>
                  <TableHead>{t('shift.dashboard.duration', 'Duration')}</TableHead>
                  <TableHead>{t('shift.dashboard.orders', 'Orders')}</TableHead>
                  <TableHead>{t('shift.dashboard.sales', 'Sales')}</TableHead>
                  <TableHead>{t('shift.dashboard.cashSales', 'Cash')}</TableHead>
                  <TableHead>{t('shift.dashboard.cardSales', 'Card')}</TableHead>
                  <TableHead className="text-center">{t('shift.dashboard.status', 'Status')}</TableHead>
                  <TableHead className="text-right">{t('common.actions', 'Actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {activeShifts.map(shift => {
                  const liveMin = getLiveMinutes(shift.clockIn);
                  const overtime = isOvertime(shift.clockIn);
                  const nearOvertime = isApproachingOvertime(shift.clockIn);

                  return (
                    <TableRow key={shift.id} className={overtime ? 'bg-red-50' : nearOvertime ? 'bg-amber-50' : ''}>
                      <TableCell className="font-medium">
                        {shift.employeeName}
                        {overtime && <AlertTriangle className="inline ml-2 h-4 w-4 text-red-500" />}
                        {nearOvertime && !overtime && <AlertTriangle className="inline ml-2 h-4 w-4 text-amber-500" />}
                      </TableCell>
                      <TableCell>
                        {shift.clockIn ? new Date(shift.clockIn).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '--'}
                      </TableCell>
                      <TableCell className={overtime ? 'text-red-600 font-bold' : ''}>
                        {formatDuration(liveMin)}
                      </TableCell>
                      <TableCell>{shift.totalOrders || 0}</TableCell>
                      <TableCell>{shift.totalSales ? Number(shift.totalSales).toLocaleString() : '0'}</TableCell>
                      <TableCell>{shift.totalCashSales ? Number(shift.totalCashSales).toLocaleString() : '0'}</TableCell>
                      <TableCell>{shift.totalCardSales ? Number(shift.totalCardSales).toLocaleString() : '0'}</TableCell>
                      <TableCell className="text-center">
                        <Badge variant={shift.status === 'ON_BREAK' ? 'outline' : 'default'}>
                          {shift.status === 'ON_BREAK'
                            ? t('shift.dashboard.onBreakStatus', 'Break')
                            : t('shift.dashboard.activeStatus', 'Active')}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex gap-1 justify-end">
                          {shift.status === 'ACTIVE' && (
                            <Button variant="outline" size="sm" onClick={() => handleStartBreak(shift.id)}>
                              <Coffee className="h-3 w-3 mr-1" />
                              {t('shift.dashboard.break', 'Break')}
                            </Button>
                          )}
                          {shift.status === 'ON_BREAK' && (
                            <Button variant="outline" size="sm" onClick={() => handleEndBreak(shift.id)}>
                              <UserCheck className="h-3 w-3 mr-1" />
                              {t('shift.dashboard.endBreak', 'End Break')}
                            </Button>
                          )}
                          <Button variant="outline" size="sm" onClick={() => handleClockOut(shift.id)}>
                            <LogOut className="h-3 w-3 mr-1" />
                            {t('shift.dashboard.clockOut', 'Clock Out')}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Completed shifts in the selected date range */}
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div>
              <CardTitle className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5" />
                {t('shift.dashboard.completedInRange', 'Completed shifts')}
              </CardTitle>
              <CardDescription>
                {dateRange.from === dateRange.to
                  ? t('shift.dashboard.singleDay', 'On {{date}}', { date: dateRange.from })
                  : t('shift.dashboard.range', 'From {{from}} to {{to}}', {
                      from: dateRange.from, to: dateRange.to,
                    })}
              </CardDescription>
            </div>
            <div className="flex items-end gap-2 flex-wrap">
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">{t('shift.dashboard.from', 'From')}</label>
                <input
                  type="date"
                  value={dateRange.from}
                  max={dateRange.to}
                  onChange={e => setDateRange(r => ({ ...r, from: e.target.value }))}
                  className="border rounded-md px-3 py-1.5 text-sm bg-background"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">{t('shift.dashboard.to', 'To')}</label>
                <input
                  type="date"
                  value={dateRange.to}
                  min={dateRange.from}
                  max={today}
                  onChange={e => setDateRange(r => ({ ...r, to: e.target.value }))}
                  className="border rounded-md px-3 py-1.5 text-sm bg-background"
                />
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setDateRange({ from: today, to: today })}
                disabled={dateRange.from === today && dateRange.to === today}
              >
                {t('shift.dashboard.today', 'Today')}
              </Button>
            </div>
          </div>
        </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('shift.dashboard.employee', 'Employee')}</TableHead>
                  <TableHead>{t('shift.dashboard.time', 'Time')}</TableHead>
                  <TableHead>{t('shift.dashboard.duration', 'Duration')}</TableHead>
                  <TableHead>{t('shift.dashboard.orders', 'Orders')}</TableHead>
                  <TableHead>{t('shift.dashboard.sales', 'Sales')}</TableHead>
                  <TableHead>{t('shift.dashboard.cashSales', 'Cash')}</TableHead>
                  <TableHead>{t('shift.dashboard.cardSales', 'Card')}</TableHead>
                  <TableHead className="text-center">{t('shift.dashboard.status', 'Status')}</TableHead>
                  <TableHead className="text-right">{t('common.actions', 'Actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {completedShifts.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9} className="text-center text-muted-foreground py-8">
                      {t('shift.dashboard.noCompletedInRange', 'No completed shifts in this range.')}
                    </TableCell>
                  </TableRow>
                )}
                {completedShifts.map(shift => (
                  <TableRow key={shift.id}>
                    <TableCell className="font-medium">{shift.employeeName}</TableCell>
                    <TableCell>
                      {shift.clockIn ? new Date(shift.clockIn).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '--'}
                      {' — '}
                      {shift.clockOut ? new Date(shift.clockOut).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '--'}
                    </TableCell>
                    <TableCell>{formatDuration(shift.workedMinutes)}</TableCell>
                    <TableCell>{shift.totalOrders || 0}</TableCell>
                    <TableCell>{shift.totalSales ? Number(shift.totalSales).toLocaleString() : '0'}</TableCell>
                      <TableCell>{shift.totalCashSales ? Number(shift.totalCashSales).toLocaleString() : '0'}</TableCell>
                      <TableCell>{shift.totalCardSales ? Number(shift.totalCardSales).toLocaleString() : '0'}</TableCell>
                    <TableCell className="text-center">
                      <Badge variant={shift.status === 'APPROVED' ? 'default' : 'secondary'}>
                        {shift.status === 'APPROVED'
                          ? t('shift.dashboard.approved', 'Approved')
                          : t('shift.dashboard.pending', 'Pending')}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex gap-1 justify-end">
                        {shift.status !== 'APPROVED' && (
                          <Button variant="outline" size="sm" onClick={() => handleApprove(shift.id)}>
                            <CheckCircle className="h-3 w-3 mr-1" />
                            {t('shift.dashboard.approve', 'Approve')}
                          </Button>
                        )}
                        <Button variant="ghost" size="sm" onClick={() => handleResendTelegram(shift.id)} title={t('shift.dashboard.sendTelegram', 'Send to Telegram')}>
                          <Send className="h-3 w-3" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
    </div>
  );
}
