import { useEffect, useState } from 'react';
import { notifyError, errorMessage } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { telegramAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Switch } from '../components/ui/switch';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '../components/ui/dialog';
import { Bot, Trash2, Settings, RefreshCw } from 'lucide-react';

const NOTIFY_FLAGS = [
  'notifyNewOrder',
  'notifyNewReservation',
  'notifyOrderCancelled',
  'notifyReservationCancelled',
  'notifyLowStock',
  'notifyCustomerReview',
  'notifyDailyReport',
  'notifyCriticalAlerts',
];

export default function OwnerBotSubscribers() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('all');
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  // EH-3: a failed subscribers load shows an error + retry instead of the "no subscribers" empty row.
  const [loadError, setLoadError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [editingDetail, setEditingDetail] = useState(null);

  useEffect(() => {
    restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
      const list = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
    }).catch(console.error);
  }, []);

  const load = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const params = selectedRestaurant !== 'all' ? { restaurantId: selectedRestaurant } : {};
      const res = await telegramAPI.listOwnerSubscribers(params);
      setRows(res.data?.data || []);
    } catch (e) {
      console.error('Failed to load subscribers:', e);
      setLoadError(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [selectedRestaurant]);

  const openEdit = async (id) => {
    try {
      const res = await telegramAPI.getOwnerSubscriber(id);
      setEditingDetail(res.data?.data);
      setEditing(id);
    } catch (e) {
      console.error(e);
      notifyError(e);
    }
  };

  const handleToggleActive = async (sub) => {
    try {
      await telegramAPI.updateOwnerSubscriber(sub.id, { isActive: !sub.isActive });
      load();
    } catch (e) {
      notifyError(e);
    }
  };

  const handleSaveEdit = async () => {
    if (!editingDetail) return;
    try {
      const { id, isActive, role, settings } = editingDetail;
      await telegramAPI.updateOwnerSubscriber(id, { isActive, role, settings });
      setEditing(null);
      setEditingDetail(null);
      load();
    } catch (e) {
      notifyError(e);
    }
  };

  const handleDelete = async (sub) => {
    if (!window.confirm(t('ownerBotSubscribers.confirmDelete',
        { defaultValue: 'Remove {{name}} from subscribers? They will stop receiving Telegram notifications.',
          name: sub.displayName }))) {
      return;
    }
    try {
      await telegramAPI.deleteOwnerSubscriber(sub.id);
      load();
    } catch (e) {
      notifyError(e);
    }
  };

  const formatTime = (iso) => iso ? new Date(iso).toLocaleString() : '—';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {t('ownerBotSubscribers.title', 'Telegram bot subscribers')}
          </h1>
          <p className="text-muted-foreground">
            {t('ownerBotSubscribers.subtitle',
              'People receiving notifications from your owner Telegram bot.')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
            <SelectTrigger className="w-[220px]"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">{t('common.allRestaurants', 'All restaurants')}</SelectItem>
              {restaurants.map(r => (
                <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" size="icon" onClick={load} disabled={loading}>
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Bot className="h-5 w-5" />
            {t('ownerBotSubscribers.list', 'Subscribers')}
          </CardTitle>
          <CardDescription>
            {t('ownerBotSubscribers.listDesc',
              'Toggle the active flag to silence a subscriber, or open settings to control which event types they receive.')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('ownerBotSubscribers.name', 'Name')}</TableHead>
                <TableHead>{t('ownerBotSubscribers.username', 'Username')}</TableHead>
                <TableHead>{t('ownerBotSubscribers.role', 'Role')}</TableHead>
                <TableHead>{t('ownerBotSubscribers.restaurant', 'Restaurant')}</TableHead>
                <TableHead className="text-center">{t('ownerBotSubscribers.verified', 'Verified')}</TableHead>
                <TableHead className="text-center">{t('ownerBotSubscribers.active', 'Active')}</TableHead>
                <TableHead>{t('ownerBotSubscribers.lastInteraction', 'Last interaction')}</TableHead>
                <TableHead className="text-right">{t('common.actions', 'Actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loadError ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center py-8">
                    <p className="text-muted-foreground mb-3">{errorMessage(loadError)}</p>
                    <Button variant="outline" size="sm" onClick={load}>
                      {t('common.retry', 'Try again')}
                    </Button>
                  </TableCell>
                </TableRow>
              ) : rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                    {t('ownerBotSubscribers.empty',
                      'No subscribers yet. Generate a code in the Telegram bot config and send it from the user\'s Telegram account.')}
                  </TableCell>
                </TableRow>
              ) : rows.map(sub => (
                <TableRow key={sub.id}>
                  <TableCell className="font-medium">{sub.displayName || '—'}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {sub.username ? `@${sub.username}` : `id:${sub.telegramUserId}`}
                  </TableCell>
                  <TableCell>
                    {sub.role
                      ? <Badge variant="outline">{sub.role}</Badge>
                      : <span className="text-muted-foreground text-sm">—</span>}
                  </TableCell>
                  <TableCell>{sub.restaurantName || '—'}</TableCell>
                  <TableCell className="text-center">
                    <Badge variant={sub.isVerified ? 'default' : 'secondary'}>
                      {sub.isVerified
                        ? t('ownerBotSubscribers.yes', 'Yes')
                        : t('ownerBotSubscribers.no', 'No')}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-center">
                    <Switch
                      checked={Boolean(sub.isActive)}
                      onCheckedChange={() => handleToggleActive(sub)}
                    />
                  </TableCell>
                  <TableCell className="text-sm">{formatTime(sub.lastInteractionAt)}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button variant="outline" size="sm" onClick={() => openEdit(sub.id)}>
                        <Settings className="h-3 w-3 mr-1" />
                        {t('ownerBotSubscribers.editSettings', 'Settings')}
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => handleDelete(sub)}>
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

      {/* Edit dialog */}
      <Dialog open={editing != null} onOpenChange={(open) => { if (!open) { setEditing(null); setEditingDetail(null); } }}>
        <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {t('ownerBotSubscribers.editTitle', 'Edit subscriber')}
            </DialogTitle>
            <DialogDescription>{editingDetail?.displayName}</DialogDescription>
          </DialogHeader>
          {editingDetail && (
            <div className="space-y-4 py-2">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label>{t('ownerBotSubscribers.role', 'Role')}</Label>
                  <Input
                    value={editingDetail.role || ''}
                    onChange={e => setEditingDetail({ ...editingDetail, role: e.target.value })}
                    placeholder="OWNER / MANAGER / …"
                  />
                </div>
                <div className="flex items-center justify-between rounded-md border px-3 py-2">
                  <Label>{t('ownerBotSubscribers.active', 'Active')}</Label>
                  <Switch
                    checked={Boolean(editingDetail.isActive)}
                    onCheckedChange={v => setEditingDetail({ ...editingDetail, isActive: v })}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <h3 className="font-semibold text-sm">
                  {t('ownerBotSubscribers.notificationFlags', 'Notify on…')}
                </h3>
                <div className="grid grid-cols-1 gap-2">
                  {NOTIFY_FLAGS.map(flag => (
                    <div key={flag} className="flex items-center justify-between rounded-md border px-3 py-2">
                      <Label className="cursor-pointer">
                        {t(`ownerBotSubscribers.flags.${flag}`, flag)}
                      </Label>
                      <Switch
                        checked={Boolean(editingDetail.settings?.[flag])}
                        onCheckedChange={v => setEditingDetail({
                          ...editingDetail,
                          settings: { ...(editingDetail.settings || {}), [flag]: v },
                        })}
                      />
                    </div>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <h3 className="font-semibold text-sm">
                  {t('ownerBotSubscribers.thresholds', 'Thresholds')}
                </h3>
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <Label className="text-xs">
                      {t('ownerBotSubscribers.minOrderAmount', 'Min order amount for notify')}
                    </Label>
                    <Input
                      type="number"
                      value={editingDetail.settings?.minOrderAmountNotify ?? 0}
                      onChange={e => setEditingDetail({
                        ...editingDetail,
                        settings: { ...(editingDetail.settings || {}),
                          minOrderAmountNotify: e.target.value === '' ? 0 : parseFloat(e.target.value) },
                      })}
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs">
                      {t('ownerBotSubscribers.lowStockThreshold', 'Low stock threshold')}
                    </Label>
                    <Input
                      type="number"
                      value={editingDetail.settings?.lowStockThreshold ?? 10}
                      onChange={e => setEditingDetail({
                        ...editingDetail,
                        settings: { ...(editingDetail.settings || {}),
                          lowStockThreshold: e.target.value === '' ? 10 : parseInt(e.target.value, 10) },
                      })}
                    />
                  </div>
                </div>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => { setEditing(null); setEditingDetail(null); }}>
              {t('common.cancel', 'Cancel')}
            </Button>
            <Button onClick={handleSaveEdit}>{t('common.save', 'Save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
