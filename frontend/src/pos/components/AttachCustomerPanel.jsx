import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { customerAPI, loyaltyAPI, posAPI } from '../../services/api';
import { QrCode, Phone, UserCheck, X } from 'lucide-react';

/**
 * Lets the cashier link an existing customer to the current POS order so
 * the loyalty wallet credit fires on payment completion. Identifies the
 * customer either by scanning their loyalty QR (USB scanner pastes the
 * code into the focused input and submits with Enter) or by phone search.
 *
 * Renders nothing when the order hasn't been persisted yet — the backend
 * needs a real order id to attach to.
 */
export default function AttachCustomerPanel({ orderId }) {
  const { t } = useTranslation();
  const persistedOrderId =
    orderId && !String(orderId).startsWith('temp-') ? orderId : null;

  const [attached, setAttached] = useState(null);
  const [loyalty, setLoyalty] = useState(null);
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState('qr');
  const [qrInput, setQrInput] = useState('');
  const [phoneInput, setPhoneInput] = useState('');
  const [phoneResults, setPhoneResults] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const qrInputRef = useRef(null);

  // V182 welcome bonus. A phone lookup that finds nobody is the moment a walk-in can be turned into a
  // known customer, so the offer lives exactly there rather than in a panel of its own.
  const [bonusOffer, setBonusOffer] = useState({ enabled: false, amount: 0 });
  const [regName, setRegName] = useState('');
  const [registering, setRegistering] = useState(false);
  const [registered, setRegistered] = useState(null); // { name, bonusGranted }

  useEffect(() => {
    if (open && mode === 'qr') {
      // Focus immediately so a USB scanner's keystrokes are captured.
      setTimeout(() => qrInputRef.current?.focus(), 50);
    }
  }, [open, mode]);

  // Read the welcome-bonus setting once the dialog opens, so the offer can quote the real amount.
  // Non-fatal: if it fails, the register option simply stays hidden and lookup still works.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    loyaltyAPI.getConfig()
      .then((res) => {
        const cfg = res.data?.data ?? res.data ?? {};
        if (!cancelled) {
          setBonusOffer({
            enabled: !!cfg.registrationBonusEnabled && Number(cfg.registrationBonusAmount) > 0,
            amount: Number(cfg.registrationBonusAmount) || 0,
          });
        }
      })
      .catch(() => { if (!cancelled) setBonusOffer({ enabled: false, amount: 0 }); });
    return () => { cancelled = true; };
  }, [open]);

  /**
   * Register the walk-in whose phone just came back empty. No OTP — the cashier is the check — so this
   * is two fields and done, which is the only version of the offer that survives a queue.
   */
  const handleStaffRegister = async (e) => {
    e.preventDefault();
    const phone = phoneInput.trim();
    if (!phone || !regName.trim()) return;
    try {
      setRegistering(true);
      setError(null);
      const res = await customerAPI.staffRegister({
        orderId: persistedOrderId,
        firstName: regName.trim(),
        phone,
      });
      const result = res.data?.data ?? res.data ?? {};
      setRegistered({
        name: result.customerName || regName.trim(),
        bonusGranted: Number(result.bonusGranted) || 0,
      });
      // Route through the existing attach path so the panel's own state stays consistent.
      if (result.customerId) {
        await finishAttach({ customerId: result.customerId });
      }
      setRegName('');
    } catch (err) {
      console.error('Staff registration failed:', err);
      setError(err?.response?.data?.message
        || t('pos.attachCustomer.registerFailed', 'Could not register this guest.'));
    } finally {
      setRegistering(false);
    }
  };

  const loadLoyalty = async (customerId) => {
    try {
      const res = await loyaltyAPI.getCustomerLoyalty(customerId);
      setLoyalty(res.data.data);
    } catch (err) {
      console.warn('Failed to load loyalty for attached customer:', err);
      setLoyalty(null);
    }
  };

  const finishAttach = async (payload) => {
    if (!persistedOrderId) {
      setError(t('pos.attachCustomer.noOrderYet',
        'Save the order before attaching a customer.'));
      return;
    }
    try {
      setBusy(true);
      setError(null);
      const res = await posAPI.attachCustomer(persistedOrderId, payload);
      const updated = res.data.data?.customer || null;
      setAttached(updated);
      if (updated?.id) await loadLoyalty(updated.id);
      setOpen(false);
      setQrInput('');
      setPhoneInput('');
      setPhoneResults([]);
    } catch (err) {
      console.error('Attach customer failed:', err);
      setError(err.response?.data?.message || err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleQrSubmit = async (e) => {
    e.preventDefault();
    const code = qrInput.trim();
    if (!code) return;
    finishAttach({ qrCode: code });
  };

  const handlePhoneSearch = async (e) => {
    e?.preventDefault();
    const q = phoneInput.trim();
    if (!q) {
      setPhoneResults([]);
      return;
    }
    try {
      setBusy(true);
      const res = await customerAPI.suggestByPhone(q);
      setPhoneResults(res.data.data || []);
    } catch (err) {
      console.error('Phone search failed:', err);
      setPhoneResults([]);
    } finally {
      setBusy(false);
    }
  };

  const handlePickResult = (c) => finishAttach({ customerId: c.id });

  if (!persistedOrderId) return null;

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-3 sm:p-4 flex items-center justify-between gap-3">
      <div className="flex items-center gap-3 min-w-0">
        <div className="p-2 bg-blue-50 rounded-lg">
          <UserCheck className="w-5 h-5 text-blue-600" />
        </div>
        <div className="min-w-0">
          <div className="text-xs uppercase tracking-wide text-gray-500">
            {t('pos.attachCustomer.label', 'Loyalty customer')}
          </div>
          {attached ? (
            <div className="flex flex-wrap items-baseline gap-x-3">
              <span className="font-medium truncate">
                {attached.firstName} {attached.lastName}
              </span>
              <span className="text-sm text-gray-500 truncate">{attached.phone}</span>
              {loyalty && (
                <span className="text-sm text-blue-700">
                  {t('pos.attachCustomer.balance', 'Balance')}: {loyalty.currentBalance ?? 0}
                  {loyalty.tier?.name ? ` · ${loyalty.tier.name}` : ''}
                </span>
              )}
            </div>
          ) : (
            <div className="text-gray-600 text-sm">
              {t('pos.attachCustomer.notAttached',
                'Not attached — wallet bonus will not be credited.')}
            </div>
          )}
        </div>
      </div>
      <div className="flex gap-2 shrink-0">
        {attached && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => { setAttached(null); setLoyalty(null); }}
            title={t('pos.attachCustomer.clearLocal', 'Clear local selection')}
          >
            <X className="w-4 h-4" />
          </Button>
        )}
        <Button onClick={() => { setError(null); setOpen(true); }}>
          {attached
            ? t('pos.attachCustomer.change', 'Change')
            : t('pos.attachCustomer.attach', 'Attach')}
        </Button>
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>
              {t('pos.attachCustomer.dialogTitle', 'Attach customer to order')}
            </DialogTitle>
            <DialogDescription>
              {t('pos.attachCustomer.dialogDescription',
                'Identify the customer by scanning their loyalty QR or looking them up by phone.')}
            </DialogDescription>
          </DialogHeader>

          <div className="flex gap-2 mb-3">
            <Button
              variant={mode === 'qr' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('qr')}
            >
              <QrCode className="w-4 h-4 mr-2" />
              {t('pos.attachCustomer.modes.qr', 'Scan QR')}
            </Button>
            <Button
              variant={mode === 'phone' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setMode('phone')}
            >
              <Phone className="w-4 h-4 mr-2" />
              {t('pos.attachCustomer.modes.phone', 'Phone lookup')}
            </Button>
          </div>

          {error && (
            <div className="rounded border border-red-200 bg-red-50 text-red-700 text-sm p-2 mb-2">
              {error}
            </div>
          )}

          {mode === 'qr' && (
            <form onSubmit={handleQrSubmit} className="space-y-2">
              <Input
                ref={qrInputRef}
                placeholder={t('pos.attachCustomer.qrPlaceholder',
                  'Scan or type the customer QR code…')}
                value={qrInput}
                onChange={(e) => setQrInput(e.target.value)}
                autoFocus
              />
              <div className="text-xs text-gray-500">
                {t('pos.attachCustomer.qrHint',
                  'USB scanners will submit automatically on Enter.')}
              </div>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                  {t('common.cancel', 'Cancel')}
                </Button>
                <Button type="submit" disabled={busy || !qrInput.trim()}>
                  {busy
                    ? t('common.loading', 'Loading...')
                    : t('pos.attachCustomer.attach', 'Attach')}
                </Button>
              </DialogFooter>
            </form>
          )}

          {mode === 'phone' && (
            <div className="space-y-3">
              <form onSubmit={handlePhoneSearch} className="flex gap-2">
                <Input
                  placeholder={t('pos.attachCustomer.phonePlaceholder',
                    'Phone number…')}
                  value={phoneInput}
                  onChange={(e) => setPhoneInput(e.target.value)}
                  autoFocus
                />
                <Button type="submit" variant="outline" disabled={busy}>
                  {t('common.search', 'Search')}
                </Button>
              </form>
              {phoneResults.length > 0 && (
                <div className="border rounded-md divide-y max-h-60 overflow-y-auto">
                  {phoneResults.map((c) => (
                    <button
                      key={c.id}
                      type="button"
                      className="w-full text-left px-3 py-2 hover:bg-muted transition disabled:opacity-50"
                      disabled={busy}
                      onClick={() => handlePickResult(c)}
                    >
                      <div className="font-medium">{c.firstName} {c.lastName}</div>
                      <div className="text-xs text-gray-500">{c.phone}</div>
                    </button>
                  ))}
                </div>
              )}
              {!busy && phoneInput.trim() && phoneResults.length === 0 && !registered && (
                <div className="space-y-3">
                  <div className="text-sm text-gray-500">
                    {t('pos.attachCustomer.noMatches', 'No matches')}
                  </div>

                  {/* Nobody on that number — the one moment a walk-in can become a known guest. */}
                  {bonusOffer.enabled && (
                    <form onSubmit={handleStaffRegister} className="space-y-2 rounded-md border p-3">
                      <div className="text-sm font-medium">
                        {t('pos.attachCustomer.registerOffer',
                          'Register this guest and give them {{amount}} bonus',
                          { amount: bonusOffer.amount })}
                      </div>
                      <Input
                        placeholder={t('pos.attachCustomer.registerNamePlaceholder', 'Guest name')}
                        value={regName}
                        onChange={(e) => setRegName(e.target.value)}
                        maxLength={100}
                      />
                      <div className="text-xs text-gray-500">
                        {t('pos.attachCustomer.registerPhoneNote', 'Phone: {{phone}}',
                          { phone: phoneInput.trim() })}
                      </div>
                      <Button type="submit" size="sm" disabled={registering || !regName.trim()}>
                        {registering
                          ? t('common.loading', 'Loading...')
                          : t('pos.attachCustomer.registerAction', 'Register & give bonus')}
                      </Button>
                    </form>
                  )}
                </div>
              )}

              {/* Say what actually happened — a zero means this guest already had the welcome bonus,
                  which the cashier needs to know before promising it out loud. */}
              {registered && (
                <div className="rounded-md border border-green-200 bg-green-50 p-3 text-sm text-green-800">
                  {registered.bonusGranted > 0
                    ? t('pos.attachCustomer.registeredWithBonus',
                        '{{name}} registered — {{amount}} bonus credited.',
                        { name: registered.name, amount: registered.bonusGranted })
                    : t('pos.attachCustomer.registeredNoBonus',
                        '{{name}} registered. No welcome bonus — they have already had one.',
                        { name: registered.name })}
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
