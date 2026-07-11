import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { restaurantAPI, systemUserAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
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
import { Plus, Pencil, Trash2, ShieldCheck } from 'lucide-react';
import PasswordInput from '../components/PasswordInput';

const ROLES = ['ADMIN', 'OWNER', 'MANAGER', 'OPERATOR'];

export default function SystemUsers() {
  const { t } = useTranslation();
  const currentUser = useAuthStore((state) => state.user);
  // Only the platform operator may pick which restaurant a user belongs to (the backend enforces
  // this too); tenant admins always create within their own restaurant.
  const isSuperAdmin = currentUser?.role === 'SUPER_ADMIN';
  const [users, setUsers] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [form, setForm] = useState({
    email: '', password: '', firstName: '', lastName: '', phone: '', role: 'MANAGER', active: true, restaurantId: '',
  });

  useEffect(() => { loadUsers(); }, []);

  useEffect(() => {
    if (!isSuperAdmin) return;
    restaurantAPI.getAll()
      .then((res) => setRestaurants(res.data?.data?.content || res.data?.data || []))
      .catch((e) => console.error(e));
  }, [isSuperAdmin]);

  const restaurantName = (id) => {
    if (id == null) return '—';
    const r = restaurants.find((x) => x.id === id);
    return r ? r.name : `#${id}`;
  };

  const loadUsers = async () => {
    try {
      const res = await systemUserAPI.getAll();
      setUsers(res.data.data || []);
    } catch (e) { console.error(e); }
  };

  const openCreate = () => {
    setEditingUser(null);
    setForm({ email: '', password: '', firstName: '', lastName: '', phone: '', role: 'MANAGER', active: true, restaurantId: '' });
    setDialogOpen(true);
  };

  const openEdit = (user) => {
    setEditingUser(user);
    setForm({ ...user, password: '', restaurantId: user.restaurantId ?? '' });
    setDialogOpen(true);
  };

  const handleSave = async () => {
    try {
      const data = { ...form };
      // Empty selection means "platform account" on create and "leave unchanged" on edit; the
      // backend treats an absent restaurantId exactly that way, so drop the key instead of
      // sending an empty string.
      if (data.restaurantId === '' || data.restaurantId == null) delete data.restaurantId;
      else data.restaurantId = Number(data.restaurantId);
      if (editingUser) {
        if (!data.password) delete data.password;
        await systemUserAPI.update(editingUser.id, data);
      } else {
        await systemUserAPI.create(data);
      }
      setDialogOpen(false);
      loadUsers();
    } catch (e) {
      console.error(e);
      alert(e.response?.data?.message || t('systemUsers.saveFailed', 'Failed'));
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('systemUsers.confirmDeactivate', 'Deactivate this user?'))) return;
    try {
      await systemUserAPI.delete(id);
      loadUsers();
    } catch (e) { console.error(e); }
  };

  const roleColor = (role) => {
    switch (role) {
      case 'ADMIN': return 'destructive';
      case 'OWNER': return 'default';
      case 'MANAGER': return 'secondary';
      default: return 'outline';
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('systemUsers.title', 'System Users')}</h1>
          <p className="text-muted-foreground">{t('systemUsers.subtitle', 'Manage users with admin panel access')}</p>
        </div>
        <Button onClick={openCreate}>
          <Plus className="h-4 w-4 mr-2" /> {t('systemUsers.addUser', 'Add User')}
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5" />
            {t('systemUsers.panelUsers', 'Control Panel Users')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('systemUsers.name', 'Name')}</TableHead>
                <TableHead>{t('systemUsers.email', 'Email')}</TableHead>
                <TableHead>{t('systemUsers.phone', 'Phone')}</TableHead>
                <TableHead className="text-center">{t('systemUsers.role', 'Role')}</TableHead>
                {isSuperAdmin && <TableHead>{t('systemUsers.restaurant', 'Restaurant')}</TableHead>}
                <TableHead className="text-center">{t('systemUsers.status', 'Status')}</TableHead>
                <TableHead className="text-right">{t('systemUsers.actions', 'Actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {users.length === 0 ? (
                <TableRow><TableCell colSpan={isSuperAdmin ? 7 : 6} className="text-center py-8 text-muted-foreground">{t('systemUsers.noUsers', 'No system users')}</TableCell></TableRow>
              ) : (
                users.map(u => (
                  <TableRow key={u.id}>
                    <TableCell className="font-medium">{u.firstName} {u.lastName}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{u.phone || '—'}</TableCell>
                    <TableCell className="text-center">
                      <Badge variant={roleColor(u.role)}>{u.role}</Badge>
                    </TableCell>
                    {isSuperAdmin && <TableCell>{restaurantName(u.restaurantId)}</TableCell>}
                    <TableCell className="text-center">
                      <Badge variant={u.active ? 'default' : 'secondary'}>
                        {u.active ? t('systemUsers.active', 'Active') : t('systemUsers.inactive', 'Inactive')}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex gap-1 justify-end">
                        <Button variant="outline" size="icon" onClick={() => openEdit(u)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(u.id)}>
                          <Trash2 className="h-4 w-4 text-red-500" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>
              {editingUser ? t('systemUsers.editUser', 'Edit User') : t('systemUsers.addUser', 'Add User')}
            </DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('systemUsers.firstName', 'First Name')} *</Label>
                <Input value={form.firstName} onChange={e => setForm({ ...form, firstName: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('systemUsers.lastName', 'Last Name')} *</Label>
                <Input value={form.lastName} onChange={e => setForm({ ...form, lastName: e.target.value })} />
              </div>
            </div>
            {!editingUser && (
              <div className="space-y-2">
                <Label>{t('systemUsers.email', 'Email')} *</Label>
                <Input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
              </div>
            )}
            <div className="space-y-2">
              <Label>{editingUser ? t('systemUsers.newPassword', 'New Password (leave empty to keep)') : t('systemUsers.password', 'Password')} *</Label>
              <PasswordInput value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('systemUsers.phone', 'Phone')}</Label>
                <Input value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('systemUsers.role', 'Role')} *</Label>
                <Select value={form.role} onValueChange={v => setForm({ ...form, role: v })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {ROLES.map(r => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
            </div>
            {isSuperAdmin && (
              <div className="space-y-2">
                <Label>{t('systemUsers.restaurant', 'Restaurant')}</Label>
                <Select
                  value={form.restaurantId === '' ? 'none' : String(form.restaurantId)}
                  onValueChange={v => setForm({ ...form, restaurantId: v === 'none' ? '' : v })}
                >
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {/* On edit of a bound user "none" is omitted: the API's partial-update contract
                        can rebind but never unbind, so offering it would be a silent no-op. */}
                    {(!editingUser || editingUser.restaurantId == null) && (
                      <SelectItem value="none">{t('systemUsers.platformAccount', '— Platform (no restaurant)')}</SelectItem>
                    )}
                    {restaurants.map(r => (
                      <SelectItem key={r.id} value={String(r.id)}>{r.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  {t('systemUsers.restaurantHint', 'Which restaurant this user manages. Platform accounts have no restaurant access.')}
                </p>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleSave} disabled={!form.firstName || !form.lastName || (!editingUser && (!form.email || !form.password))}>
              {editingUser ? t('common.save') : t('common.create')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
