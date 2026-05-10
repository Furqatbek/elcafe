import React from 'react';
import { Plus, X } from 'lucide-react';
import Button from './Button';
import usePosStore from '../store';
import { TYPE_CHIP, TYPE_CHIP_COLORS, MONEY_STYLE } from '../theme';

export default function Header({ theme, themeKey, cashierName = 'Cashier' }) {
  const tickets = usePosStore((s) => s.tickets);
  const activeId = usePosStore((s) => s.activeId);
  const setActive = usePosStore((s) => s.setActive);
  const closeTicket = usePosStore((s) => s.closeTicket);
  const newTicket = usePosStore((s) => s.newTicket);
  const themeOpt = TYPE_CHIP_COLORS[themeKey] || TYPE_CHIP_COLORS.blue;

  return (
    <div
      style={{
        height: 64,
        flexShrink: 0,
        borderBottom: `1px solid ${theme.border}`,
        background: theme.surface,
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        padding: '0 16px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: 10,
            background: theme.primary,
            color: '#fff',
            fontWeight: 800,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 16,
          }}
        >
          eC
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.1 }}>
          <span style={{ fontSize: 14, fontWeight: 700, color: theme.text }}>elCafé POS</span>
          <span style={{ fontSize: 12, color: theme.textMuted }}>{cashierName}</span>
        </div>
      </div>

      <div
        style={{
          width: 1,
          height: 32,
          background: theme.border,
          margin: '0 6px',
        }}
      />

      {/* Ticket tabs */}
      <div
        style={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          overflowX: 'auto',
          scrollbarWidth: 'thin',
        }}
      >
        {tickets.length === 0 && (
          <span style={{ fontSize: 13, color: theme.textMuted, fontStyle: 'italic' }}>
            No open tickets — start one →
          </span>
        )}
        {tickets.map((t) => {
          const isActive = t.id === activeId;
          const chip = TYPE_CHIP[t.type] || TYPE_CHIP.dinein;
          const chipColors = themeOpt[t.type];
          const itemCount = t.items.reduce((s, it) => s + it.qty, 0);
          return (
            <div
              key={t.id}
              onClick={() => setActive(t.id)}
              role="button"
              style={{
                height: 44,
                padding: '0 10px 0 8px',
                borderRadius: 10,
                background: isActive ? theme.primarySoft : theme.surfaceAlt,
                border: `1px solid ${isActive ? theme.primary : theme.border}`,
                color: isActive ? theme.primary : theme.text,
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                cursor: 'pointer',
                fontWeight: 600,
                fontSize: 13,
                userSelect: 'none',
                flexShrink: 0,
              }}
            >
              <span
                style={{
                  fontSize: 10,
                  fontWeight: 800,
                  letterSpacing: 0.5,
                  padding: '2px 5px',
                  borderRadius: 4,
                  background: chipColors.bg,
                  color: chipColors.fg,
                }}
              >
                {chip.code}
              </span>
              <span style={{ maxWidth: 140, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {t.label}
              </span>
              {itemCount > 0 && (
                <span
                  style={{
                    fontSize: 11,
                    fontWeight: 700,
                    color: isActive ? theme.primary : theme.textMuted,
                    background: isActive ? '#fff' : theme.surface,
                    padding: '1px 6px',
                    borderRadius: 999,
                    border: `1px solid ${isActive ? theme.primary : theme.border}`,
                    ...MONEY_STYLE,
                  }}
                >
                  {itemCount}
                </span>
              )}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  closeTicket(t.id);
                }}
                style={{
                  background: 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  color: 'inherit',
                  padding: 2,
                  display: 'flex',
                  borderRadius: 4,
                }}
                title="Close ticket"
              >
                <X size={14} />
              </button>
            </div>
          );
        })}
      </div>

      <div style={{ display: 'flex', gap: 8 }}>
        <Button theme={theme} size="md" onClick={() => newTicket('dinein')}>
          <Plus size={16} /> Dine-in
        </Button>
        <Button theme={theme} size="md" onClick={() => newTicket('takeaway')}>
          <Plus size={16} /> Takeaway
        </Button>
        <Button theme={theme} size="md" onClick={() => newTicket('delivery')}>
          <Plus size={16} /> Delivery
        </Button>
      </div>
    </div>
  );
}
