import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { systemUserAPI } from '../../services/api';
import { useApiCall } from '../../hooks/useApiCall';
import { notifyError, notifySuccess } from '../../lib/errors';
import QueryState from '../QueryState';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Badge } from '../ui/badge';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription,
} from '../ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../ui/table';
import PasswordInput from '../PasswordInput';
import { generatePassword } from '../../utils/passwordGenerator';
import { UserPlus, Trash2, RotateCcw, RefreshCw } from 'lucide-react';

// The roles a SUPER_ADMIN may grant on a restaurant (backend SYSTEM_ROLES, minus platform-only).
const GRANTABLE_ROLES = ['OWNER', 'ADMIN', 'MANAGER', 'OPERATOR'];

const emptyForm = () => ({
  email: '', password: generatePassword(12), firstName: '', lastName: '', phone: '', role: 'MANAGER',
});

/**
 * Per-tenant access panel for a SUPER_ADMIN: who can sign in to this restaurant, and grant/revoke.
 * The list comes from GET /system-users (the operator sees every tenant's users) filtered to this
 * restaurant; grants POST /system-users with restaurantId set (server binds it, tenant admins can't).
 * Revoke = soft deactivate (DELETE, which also kills live tokens); restore = PATCH active:true.
 */
export default function TenantAccessDialog({ tenant, onOpenChange }) {
  const { t } = useTranslation();
  const open = tenant != null;
  const restaurantId = tenant?.restaurantId ?? null;

  // Keyed on restaurantId: refetches when a different tenant opens, returns [] while closed.
  const { data, loading, error, refetch } = useApiCall(
    () => (restaurantId != null
      ? systemUserAPI.getAll().then((res) => (res.data.data || []).filter((u) => u.restaurantId === restaurantId))
      : Promise.resolve([])),
    [restaurantId],
  );
  const users = data || [];

  const [form, setForm] = useState(null); // null = add form closed
  const [busy, setBusy] = useState(false);

  const formValid = form && form.email.trim() && form.password.trim()
    && form.firstName.trim() && form.lastName.trim();

  const close = () => { setForm(null); onOpenChange(false); };

  const submitAdd = async () => {
    if (!formValid || busy) return;
    setBusy(true);
    try {
      await systemUserAPI.create({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        phone: form.phone.trim() || undefined,
        role: form.role,
        restaurantId,
      });
      notifySuccess(t('platform.access.granted', 'Access granted to {{name}}',
        { name: `${form.firstName.trim()} ${form.lastName.trim()}`.trim() }));
      setForm(null);
      refetch();
    } catch (e) {
      notifyError(e);
    } finally {
      setBusy(false);
    }
  };

  const revoke = async (u) => {
    if (!window.confirm(t('platform.access.confirmRevoke',
      "Revoke access for {{email}}? They are signed out immediately and can't log in.", { email: u.email }))) {
      return;
    }
    setBusy(true);
    try {
      await systemUserAPI.delete(u.id);
      notifySuccess(t('platform.access.revoked', 'Access revoked'));
      refetch();
    } catch (e) {
      notifyError(e);
    } finally {
      setBusy(false);
    }
  };

  const restore = async (u) => {
    setBusy(true);
    try {
      await systemUserAPI.update(u.id, { active: true }); // PATCH-style: only flips active
      notifySuccess(t('platform.access.restored', 'Access restored'));
      refetch();
    } catch (e) {
      notifyError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) close(); }}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            {t('platform.access.title', 'Access — {{name}}', { name: tenant?.name || '' })}
          </DialogTitle>
          <DialogDescription>
            {t('platform.access.subtitle', 'People who can sign in to this restaurant.')}
          </DialogDescription>
        </DialogHeader>

        <QueryState
          loading={loading}
          error={error}
          onRetry={refetch}
          empty={users.length === 0}
          emptyMessage={t('platform.access.noUsers', 'No accounts yet — grant access below.')}
        >
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('platform.access.name', 'Name')}</TableHead>
                <TableHead>{t('platform.access.email', 'Email')}</TableHead>
                <TableHead>{t('platform.access.role', 'Role')}</TableHead>
                <TableHead className="text-center">{t('platform.access.status', 'Status')}</TableHead>
                <TableHead className="text-right">{t('platform.access.actions', 'Actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.map((u) => (
                <TableRow key={u.id}>
                  <TableCell className="font-medium">{`${u.firstName || ''} ${u.lastName || ''}`.trim() || '—'}</TableCell>
                  <TableCell className="text-sm text-muted-foreground">{u.email}</TableCell>
                  <TableCell><Badge variant="outline">{u.role}</Badge></TableCell>
                  <TableCell className="text-center">
                    <Badge variant={u.active ? 'default' : 'secondary'}>
                      {u.active ? t('platform.access.active', 'Active') : t('platform.access.revokedBadge', 'Revoked')}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    {u.active ? (
                      <Button variant="ghost" size="sm" disabled={busy} onClick={() => revoke(u)}>
                        <Trash2 className="h-4 w-4 mr-1 text-red-500" />
                        {t('platform.access.revoke', 'Revoke')}
                      </Button>
                    ) : (
                      <Button variant="ghost" size="sm" disabled={busy} onClick={() => restore(u)}>
                        <RotateCcw className="h-4 w-4 mr-1" />
                        {t('platform.access.restore', 'Restore')}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </QueryState>

        {/* Grant-access form */}
        {form ? (
          <div className="rounded-md border p-4 space-y-3">
            <div className="text-sm font-medium">{t('platform.access.grantTitle', 'Grant access')}</div>
            <div className="grid grid-cols-2 gap-3">
              <Input placeholder={`${t('platform.access.firstName', 'First name')} *`}
                value={form.firstName} onChange={(e) => setForm((f) => ({ ...f, firstName: e.target.value }))} />
              <Input placeholder={`${t('platform.access.lastName', 'Last name')} *`}
                value={form.lastName} onChange={(e) => setForm((f) => ({ ...f, lastName: e.target.value }))} />
              <Input className="col-span-2" type="email" placeholder={`${t('platform.access.email', 'Email')} *`}
                value={form.email} onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))} />
              <div className="space-y-1">
                <Label className="text-xs">{t('platform.access.role', 'Role')}</Label>
                <Select value={form.role} onValueChange={(v) => setForm((f) => ({ ...f, role: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {GRANTABLE_ROLES.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label className="text-xs">{t('platform.access.password', 'Temporary password')} *</Label>
                <div className="flex gap-2">
                  <PasswordInput value={form.password}
                    onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))} />
                  <Button type="button" variant="outline" size="icon"
                    title={t('platform.access.regenerate', 'Generate a new password')}
                    onClick={() => setForm((f) => ({ ...f, password: generatePassword(12) }))}>
                    <RefreshCw className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" disabled={busy} onClick={() => setForm(null)}>
                {t('common.cancel', 'Cancel')}
              </Button>
              <Button type="button" disabled={!formValid || busy} onClick={submitAdd}>
                {t('platform.access.grant', 'Grant access')}
              </Button>
            </div>
          </div>
        ) : (
          <Button type="button" variant="outline" onClick={() => setForm(emptyForm())}>
            <UserPlus className="h-4 w-4 mr-1" />{t('platform.access.addUser', 'Add owner or employee')}
          </Button>
        )}
      </DialogContent>
    </Dialog>
  );
}
