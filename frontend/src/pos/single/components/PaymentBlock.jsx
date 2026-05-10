import React from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronLeft } from 'lucide-react';
import Button from './Button';
import { fmtMoney, MONEY_STYLE } from '../theme';
import usePosStore, { ticketSubtotal, ticketTax, ticketTotal } from '../store';

const TAX_RATE = 0.12;
const QUICK_TENDERS = [50000, 100000, 200000, 500000];

export default function PaymentBlock({ theme, ticket, restaurantId, restaurantCity, onBack, onCharged }) {
  const { t } = useTranslation();
  const setPayment = usePosStore((s) => s.setPayment);
  const charge = usePosStore((s) => s.chargeActive);
  const clearError = usePosStore((s) => s.clearSubmitError);

  const subtotal = ticketSubtotal(ticket);
  const tax = ticketTax(ticket, TAX_RATE);
  const total = ticketTotal(ticket, TAX_RATE);
  const method = ticket.payment?.method || 'cash';
  const tendered = ticket.payment?.tendered || 0;
  const change = method === 'cash' ? Math.max(0, tendered - total) : 0;
  const submitting = !!ticket.submitting;
  const submitError = ticket.submitError;

  const canCharge =
    total > 0 && !submitting && (method !== 'cash' || tendered >= total);

  const handleCharge = async () => {
    const result = await charge(restaurantId, { restaurantCity });
    if (result.success && onCharged) onCharged(result);
  };

  return (
    <div
      style={{
        background: theme.surface,
        borderTop: `1px solid ${theme.border}`,
        padding: 12,
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
      }}
    >
      <button
        onClick={onBack}
        style={{
          alignSelf: 'flex-start',
          background: 'transparent',
          border: 'none',
          color: theme.textMuted,
          fontSize: 13,
          fontWeight: 600,
          cursor: 'pointer',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 4,
          padding: 0,
        }}
      >
        <ChevronLeft size={14} /> {t('pos.single.back')}
      </button>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <Row theme={theme} label={t('pos.single.subtotalLabel')} value={fmtMoney(subtotal)} />
        <Row
          theme={theme}
          label={t('pos.single.tax', { percent: Math.round(TAX_RATE * 100) })}
          value={fmtMoney(tax)}
        />
        <Row
          theme={theme}
          label={t('pos.single.total')}
          value={fmtMoney(total)}
          bold
          large
        />
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr 1fr',
          gap: 4,
          background: theme.surfaceAlt,
          border: `1px solid ${theme.border}`,
          borderRadius: 10,
          padding: 4,
        }}
      >
        {['cash', 'card', 'mobile'].map((m) => {
          const active = method === m;
          const labelKey =
            m === 'cash' ? 'pos.single.methodCash'
              : m === 'card' ? 'pos.single.methodCard'
                : 'pos.single.methodMobile';
          return (
            <button
              key={m}
              onClick={() => setPayment({ method: m, tendered: m === 'cash' ? tendered : 0 })}
              style={{
                height: 36,
                borderRadius: 8,
                border: 'none',
                background: active ? theme.surface : 'transparent',
                color: active ? theme.primary : theme.textMuted,
                fontWeight: 700,
                fontSize: 13,
                cursor: 'pointer',
                textTransform: 'capitalize',
                boxShadow: active ? '0 1px 2px rgba(0,0,0,.08)' : 'none',
              }}
            >
              {t(labelKey)}
            </button>
          );
        })}
      </div>

      {method === 'cash' && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 6 }}>
            {QUICK_TENDERS.map((amt) => (
              <Button
                key={amt}
                theme={theme}
                variant={tendered === amt ? 'primary' : 'outline'}
                size="md"
                onClick={() => setPayment({ tendered: amt })}
              >
                <span style={MONEY_STYLE}>{fmtMoney(amt / 1000)}k</span>
              </Button>
            ))}
            <Button
              theme={theme}
              variant={tendered === total ? 'primary' : 'outline'}
              size="md"
              onClick={() => setPayment({ tendered: total })}
            >
              {t('pos.single.exact')}
            </Button>
          </div>

          <div>
            <input
              type="number"
              min={0}
              value={tendered || ''}
              onChange={(e) => setPayment({ tendered: Number(e.target.value) || 0 })}
              placeholder={t('pos.single.cashReceived')}
              style={{
                width: '100%',
                height: 44,
                borderRadius: 10,
                border: `1px solid ${theme.border}`,
                background: theme.surfaceAlt,
                color: theme.text,
                padding: '0 12px',
                fontSize: 16,
                fontWeight: 600,
                outline: 'none',
                ...MONEY_STYLE,
              }}
            />
          </div>

          <Row theme={theme} label={t('pos.single.change')} value={fmtMoney(change)} muted={change === 0} />
        </>
      )}

      {submitError && (
        <div
          onClick={clearError}
          style={{
            background: theme.dangerSoft,
            color: theme.danger,
            border: `1px solid ${theme.danger}`,
            borderRadius: 8,
            padding: '8px 10px',
            fontSize: 12,
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          {submitError}
        </div>
      )}

      <Button
        theme={theme}
        variant="success"
        size="xl"
        disabled={!canCharge}
        onClick={handleCharge}
        style={{ width: '100%' }}
      >
        {submitting
          ? t('pos.single.charging')
          : t('pos.single.charge', { amount: fmtMoney(total) })}
      </Button>
    </div>
  );
}

function Row({ theme, label, value, bold, large, muted }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        fontSize: large ? 18 : 13,
        fontWeight: bold ? 800 : 500,
        color: muted ? theme.textMuted : theme.text,
      }}
    >
      <span>{label}</span>
      <span style={MONEY_STYLE}>{value}</span>
    </div>
  );
}
