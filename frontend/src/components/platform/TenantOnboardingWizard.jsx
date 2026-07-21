import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { restaurantAPI, systemUserAPI } from '../../services/api';
import { notifyError, notifySuccess, notifyWarning } from '../../lib/errors';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription,
} from '../ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../ui/select';
import PasswordInput from '../PasswordInput';
import { generatePassword } from '../../utils/passwordGenerator';
import {
  Store, UserCog, Users, Plus, Trash2, ArrowLeft, ArrowRight, Loader2, RefreshCw,
} from 'lucide-react';

// Roles a SUPER_ADMIN may bind to a restaurant via POST /system-users (backend SYSTEM_ROLES).
const OWNER_ROLES = ['OWNER', 'ADMIN'];
const EMPLOYEE_ROLES = ['MANAGER', 'OPERATOR'];

const newEmployee = () => ({
  email: '', password: generatePassword(12), firstName: '', lastName: '', role: 'MANAGER',
});

/**
 * SUPER_ADMIN onboarding wizard: stand up a new tenant end-to-end. The backend has no combined
 * endpoint, so this orchestrates the documented two-step provisioning (docs/LAUNCH.md):
 *   1. POST /restaurants               → creates the tenant (auto-attaches a 14-day Pro trial)
 *   2. POST /system-users {restaurantId} → binds the owner, then each employee
 * A restaurant, once created, cannot be un-created; if the owner step fails we keep the tenant,
 * tell the operator, and let them finish from "Manage access" rather than orphan-retrying.
 */
export default function TenantOnboardingWizard({ open, onOpenChange, onComplete }) {
  const { t } = useTranslation();
  const [step, setStep] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [restaurant, setRestaurant] = useState({ name: '', address: '', city: '', phone: '', email: '' });
  const [owner, setOwner] = useState({
    email: '', password: '', firstName: '', lastName: '', phone: '', role: 'OWNER',
  });
  const [employees, setEmployees] = useState([]);

  // Fresh slate (and a fresh generated owner password) every time the wizard opens.
  useEffect(() => {
    if (!open) return;
    setStep(1);
    setSubmitting(false);
    setRestaurant({ name: '', address: '', city: '', phone: '', email: '' });
    setOwner({ email: '', password: generatePassword(12), firstName: '', lastName: '', phone: '', role: 'OWNER' });
    setEmployees([]);
  }, [open]);

  const step1Valid = restaurant.name.trim() && restaurant.address.trim();
  const step2Valid = owner.email.trim() && owner.password.trim()
    && owner.firstName.trim() && owner.lastName.trim();
  const employeesValid = employees.every(
    (e) => e.email.trim() && e.password.trim() && e.firstName.trim() && e.lastName.trim(),
  );

  const setEmp = (i, patch) => setEmployees((list) => list.map((e, idx) => (idx === i ? { ...e, ...patch } : e)));
  const removeEmp = (i) => setEmployees((list) => list.filter((_, idx) => idx !== i));

  const close = () => onOpenChange(false);

  const submit = async () => {
    if (!step1Valid || !step2Valid || !employeesValid || submitting) return;
    setSubmitting(true);

    // 1. Tenant.
    let restaurantId;
    try {
      const res = await restaurantAPI.create({
        name: restaurant.name.trim(),
        address: restaurant.address.trim(),
        city: restaurant.city.trim() || undefined,
        phone: restaurant.phone.trim() || undefined,
        email: restaurant.email.trim() || undefined,
      });
      restaurantId = res.data?.data?.id;
    } catch (e) {
      setSubmitting(false);
      notifyError(e);
      return; // nothing created — stay open so the operator can fix and retry
    }
    if (restaurantId == null) {
      setSubmitting(false);
      notifyError(new Error('The restaurant was created but returned no id.'));
      onComplete?.();
      close();
      return;
    }

    // 2. Owner. The tenant now exists and can't be rolled back — on failure keep it and hand off.
    try {
      await systemUserAPI.create({
        email: owner.email.trim(),
        password: owner.password,
        firstName: owner.firstName.trim(),
        lastName: owner.lastName.trim(),
        phone: owner.phone.trim() || undefined,
        role: owner.role,
        restaurantId,
      });
    } catch (e) {
      setSubmitting(false);
      notifyError(e, { title: t('platform.onboard.ownerFailed', 'Restaurant created, but the owner account was not — add it from Manage access.') });
      onComplete?.();
      close();
      return;
    }

    // 3. Employees (best-effort; a failure here never blocks the tenant + owner that already exist).
    let failures = 0;
    for (const emp of employees) {
      try {
        // eslint-disable-next-line no-await-in-loop
        await systemUserAPI.create({
          email: emp.email.trim(),
          password: emp.password,
          firstName: emp.firstName.trim(),
          lastName: emp.lastName.trim(),
          role: emp.role,
          restaurantId,
        });
      } catch {
        failures += 1;
      }
    }

    setSubmitting(false);
    if (failures > 0) {
      notifyWarning(t('platform.onboard.someEmployeesFailed',
        '{{name}} and its owner are set up, but {{n}} employee account(s) failed — add them from Manage access.',
        { name: restaurant.name.trim(), n: failures }));
    } else {
      notifySuccess(t('platform.onboard.success',
        '{{name}} is ready. The owner can sign in now.', { name: restaurant.name.trim() }));
    }
    onComplete?.();
    close();
  };

  const steps = [
    { n: 1, icon: Store, label: t('platform.onboard.stepRestaurant', 'Restaurant') },
    { n: 2, icon: UserCog, label: t('platform.onboard.stepOwner', 'Owner') },
    { n: 3, icon: Users, label: t('platform.onboard.stepEmployees', 'Employees') },
  ];

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o && !submitting) close(); }}>
      <DialogContent className="sm:max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t('platform.onboard.title', 'Onboard a restaurant')}</DialogTitle>
          <DialogDescription>
            {t('platform.onboard.subtitle', 'Create the tenant and grant platform access to its owner and staff.')}
          </DialogDescription>
        </DialogHeader>

        {/* Stepper */}
        <div className="flex items-center gap-2">
          {steps.map((s, i) => (
            <div key={s.n} className="flex items-center gap-2">
              <div className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-sm ${
                step === s.n ? 'bg-primary text-primary-foreground'
                  : step > s.n ? 'text-muted-foreground' : 'text-muted-foreground/60'}`}
              >
                <s.icon className="h-4 w-4" />
                <span className="hidden sm:inline">{s.label}</span>
              </div>
              {i < steps.length - 1 && <div className="h-px w-4 bg-border" />}
            </div>
          ))}
        </div>

        {/* Step 1 — restaurant */}
        {step === 1 && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2 col-span-2">
                <Label>{t('platform.onboard.name', 'Restaurant name')} *</Label>
                <Input value={restaurant.name}
                  onChange={(e) => setRestaurant((r) => ({ ...r, name: e.target.value }))} />
              </div>
              <div className="space-y-2 col-span-2">
                <Label>{t('platform.onboard.address', 'Address')} *</Label>
                <Input value={restaurant.address}
                  onChange={(e) => setRestaurant((r) => ({ ...r, address: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>{t('platform.onboard.city', 'City')}</Label>
                <Input value={restaurant.city}
                  onChange={(e) => setRestaurant((r) => ({ ...r, city: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>{t('platform.onboard.phone', 'Phone')}</Label>
                <Input value={restaurant.phone}
                  onChange={(e) => setRestaurant((r) => ({ ...r, phone: e.target.value }))} />
              </div>
              <div className="space-y-2 col-span-2">
                <Label>{t('platform.onboard.email', 'Email')}</Label>
                <Input type="email" value={restaurant.email}
                  onChange={(e) => setRestaurant((r) => ({ ...r, email: e.target.value }))} />
              </div>
            </div>
            <p className="text-xs text-muted-foreground">
              {t('platform.onboard.trialHint', 'New restaurants start on a 14-day Pro trial. Adjust the plan afterwards from the tenant row.')}
            </p>
          </div>
        )}

        {/* Step 2 — owner */}
        {step === 2 && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('platform.onboard.firstName', 'First name')} *</Label>
                <Input value={owner.firstName}
                  onChange={(e) => setOwner((o) => ({ ...o, firstName: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>{t('platform.onboard.lastName', 'Last name')} *</Label>
                <Input value={owner.lastName}
                  onChange={(e) => setOwner((o) => ({ ...o, lastName: e.target.value }))} />
              </div>
              <div className="space-y-2 col-span-2">
                <Label>{t('platform.onboard.email', 'Email')} *</Label>
                <Input type="email" value={owner.email}
                  onChange={(e) => setOwner((o) => ({ ...o, email: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>{t('platform.onboard.phone', 'Phone')}</Label>
                <Input value={owner.phone}
                  onChange={(e) => setOwner((o) => ({ ...o, phone: e.target.value }))} />
              </div>
              <div className="space-y-2">
                <Label>{t('platform.onboard.role', 'Role')}</Label>
                <Select value={owner.role} onValueChange={(v) => setOwner((o) => ({ ...o, role: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {OWNER_ROLES.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2 col-span-2">
                <Label>{t('platform.onboard.password', 'Temporary password')} *</Label>
                <div className="flex gap-2">
                  <PasswordInput value={owner.password}
                    onChange={(e) => setOwner((o) => ({ ...o, password: e.target.value }))} />
                  <Button type="button" variant="outline" size="icon"
                    title={t('platform.onboard.regenerate', 'Generate a new password')}
                    onClick={() => setOwner((o) => ({ ...o, password: generatePassword(12) }))}>
                    <RefreshCw className="h-4 w-4" />
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground">
                  {t('platform.onboard.passwordHint', 'Share this with the owner; they should change it after first sign-in.')}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Step 3 — employees (optional) */}
        {step === 3 && (
          <div className="space-y-4">
            {employees.length === 0 && (
              <p className="text-sm text-muted-foreground">
                {t('platform.onboard.noEmployees', 'Optional — add staff accounts now, or later from Manage access.')}
              </p>
            )}
            {employees.map((emp, i) => (
              <div key={i} className="rounded-md border p-3 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">
                    {t('platform.onboard.employeeN', 'Employee {{n}}', { n: i + 1 })}
                  </span>
                  <Button type="button" variant="ghost" size="icon" onClick={() => removeEmp(i)}>
                    <Trash2 className="h-4 w-4 text-red-500" />
                  </Button>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <Input placeholder={`${t('platform.onboard.firstName', 'First name')} *`}
                    value={emp.firstName} onChange={(e) => setEmp(i, { firstName: e.target.value })} />
                  <Input placeholder={`${t('platform.onboard.lastName', 'Last name')} *`}
                    value={emp.lastName} onChange={(e) => setEmp(i, { lastName: e.target.value })} />
                  <Input className="col-span-2" type="email" placeholder={`${t('platform.onboard.email', 'Email')} *`}
                    value={emp.email} onChange={(e) => setEmp(i, { email: e.target.value })} />
                  <Select value={emp.role} onValueChange={(v) => setEmp(i, { role: v })}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {EMPLOYEE_ROLES.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <div className="flex gap-2">
                    <PasswordInput value={emp.password}
                      onChange={(e) => setEmp(i, { password: e.target.value })} />
                    <Button type="button" variant="outline" size="icon"
                      title={t('platform.onboard.regenerate', 'Generate a new password')}
                      onClick={() => setEmp(i, { password: generatePassword(12) })}>
                      <RefreshCw className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            ))}
            <Button type="button" variant="outline" size="sm"
              onClick={() => setEmployees((list) => [...list, newEmployee()])}>
              <Plus className="h-4 w-4 mr-1" />{t('platform.onboard.addEmployee', 'Add employee')}
            </Button>
          </div>
        )}

        <DialogFooter className="flex-row justify-between sm:justify-between">
          <div>
            {step > 1 && (
              <Button type="button" variant="outline" disabled={submitting}
                onClick={() => setStep((s) => s - 1)}>
                <ArrowLeft className="h-4 w-4 mr-1" />{t('common.back', 'Back')}
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button type="button" variant="ghost" disabled={submitting} onClick={close}>
              {t('common.cancel', 'Cancel')}
            </Button>
            {step < 3 ? (
              <Button type="button"
                disabled={(step === 1 && !step1Valid) || (step === 2 && !step2Valid)}
                onClick={() => setStep((s) => s + 1)}>
                {t('common.next', 'Next')}<ArrowRight className="h-4 w-4 ml-1" />
              </Button>
            ) : (
              <Button type="button" disabled={!employeesValid || submitting} onClick={submit}>
                {submitting && <Loader2 className="h-4 w-4 mr-1 animate-spin" />}
                {t('platform.onboard.create', 'Create tenant')}
              </Button>
            )}
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
