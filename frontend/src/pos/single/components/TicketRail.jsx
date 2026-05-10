import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Pause, Edit3 } from 'lucide-react';
import Button from './Button';
import TypeSegments from './TypeSegments';
import TypeFields from './TypeFields';
import CartLine from './CartLine';
import ModifierEditor from './ModifierEditor';
import PaymentBlock from './PaymentBlock';
import { fmtMoney, MONEY_STYLE } from '../theme';
import usePosStore, { ticketSubtotal, ticketTotal } from '../store';

export default function TicketRail({ theme, ticket, tables, width, restaurantId, restaurantCity, onCharged }) {
  const { t } = useTranslation();
  const setType = usePosStore((s) => s.setType);
  const patchActive = usePosStore((s) => s.patchActive);
  const pauseActive = usePosStore((s) => s.pauseActive);
  const setQty = usePosStore((s) => s.setQty);
  const removeItem = usePosStore((s) => s.removeItem);
  const setItemNote = usePosStore((s) => s.setItemNote);
  const setPaymentMode = usePosStore((s) => s.setPaymentMode);
  const draft = usePosStore((s) => s.modifierDraft);

  const [editingLabel, setEditingLabel] = useState(false);
  const [labelDraft, setLabelDraft] = useState('');

  if (!ticket) {
    return (
      <div
        style={{
          width,
          flexShrink: 0,
          background: theme.surface,
          borderLeft: `1px solid ${theme.border}`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: theme.textMuted,
          padding: 24,
          textAlign: 'center',
          fontSize: 14,
        }}
      >
        {t('pos.single.noActiveTicket')}
      </div>
    );
  }

  const subtotal = ticketSubtotal(ticket);
  const total = ticketTotal(ticket);
  const itemCount = ticket.items.reduce((s, it) => s + it.qty, 0);
  const typeLabelKey =
    ticket.type === 'dinein' ? 'pos.single.typeDinein'
      : ticket.type === 'takeaway' ? 'pos.single.typeTakeaway'
        : 'pos.single.typeDelivery';
  const inPayment = !!ticket.paymentOpen;

  return (
    <div
      style={{
        width,
        flexShrink: 0,
        background: theme.surface,
        borderLeft: `1px solid ${theme.border}`,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        overflow: 'hidden',
      }}
    >
      {/* Ticket header */}
      <div style={{ padding: 12, borderBottom: `1px solid ${theme.border}`, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
            <span style={{ fontSize: 11, color: theme.textMuted, fontWeight: 600, ...MONEY_STYLE }}>
              {ticket.opened}
            </span>
            {editingLabel ? (
              <input
                value={labelDraft}
                autoFocus
                onChange={(e) => setLabelDraft(e.target.value)}
                onBlur={() => {
                  if (labelDraft.trim()) patchActive({ label: labelDraft.trim() });
                  setEditingLabel(false);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') e.target.blur();
                  if (e.key === 'Escape') setEditingLabel(false);
                }}
                style={{
                  flex: 1,
                  height: 28,
                  fontSize: 16,
                  fontWeight: 700,
                  borderRadius: 6,
                  border: `1px solid ${theme.primary}`,
                  background: theme.surface,
                  color: theme.text,
                  padding: '0 8px',
                  outline: 'none',
                }}
              />
            ) : (
              <button
                onClick={() => {
                  setLabelDraft(ticket.label);
                  setEditingLabel(true);
                }}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  fontSize: 16,
                  fontWeight: 700,
                  color: theme.text,
                  background: 'transparent',
                  border: 'none',
                  padding: 0,
                  cursor: 'pointer',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  minWidth: 0,
                }}
                title={t('pos.single.editLabel')}
              >
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {ticket.label}
                </span>
                <Edit3 size={12} style={{ color: theme.textMuted, flexShrink: 0 }} />
              </button>
            )}
          </div>
          <Button theme={theme} size="sm" variant="outline" onClick={pauseActive}>
            <Pause size={12} /> {t('pos.single.pause')}
          </Button>
        </div>

        <TypeSegments theme={theme} value={ticket.type} onChange={setType} />
        <TypeFields theme={theme} ticket={ticket} tables={tables} />
      </div>

      {/* Items area */}
      <div style={{ flex: 1, overflowY: 'auto', padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
        {draft && <ModifierEditor theme={theme} />}
        {ticket.items.length === 0 && !draft ? (
          <div
            style={{
              flex: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: theme.textMuted,
              fontSize: 13,
              textAlign: 'center',
              padding: 24,
            }}
          >
            {t('pos.single.tapToStart')}
          </div>
        ) : (
          ticket.items.map((line) => (
            <CartLine
              key={line.lineId}
              theme={theme}
              line={line}
              onSetQty={(q) => setQty(line.lineId, q)}
              onRemove={() => removeItem(line.lineId)}
              onSetNote={(n) => setItemNote(line.lineId, n)}
            />
          ))
        )}
      </div>

      {/* Footer: subtotal + Pay, OR PaymentBlock */}
      {inPayment ? (
        <PaymentBlock
          theme={theme}
          ticket={ticket}
          restaurantId={restaurantId}
          restaurantCity={restaurantCity}
          onBack={() => setPaymentMode(false)}
          onCharged={onCharged}
        />
      ) : (
        <div
          style={{
            background: theme.surface,
            borderTop: `1px solid ${theme.border}`,
            padding: 12,
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: theme.textMuted }}>
            <span>
              {t('pos.single.items', { count: itemCount })} · {t(typeLabelKey)}
            </span>
            <span style={MONEY_STYLE}>{t('pos.single.subtotalLabel')} {fmtMoney(subtotal)}</span>
          </div>
          <Button
            theme={theme}
            variant="primary"
            size="xl"
            disabled={ticket.items.length === 0}
            onClick={() => setPaymentMode(true)}
            style={{ width: '100%' }}
          >
            {t('pos.single.pay')} · <span style={MONEY_STYLE}>{fmtMoney(total)}</span>
          </Button>
        </div>
      )}
    </div>
  );
}
