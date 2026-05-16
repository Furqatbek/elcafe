import React from 'react';
import { useTranslation } from 'react-i18next';
import usePosStore from '../store';

function inputStyle(theme) {
  return {
    width: '100%',
    height: 40,
    borderRadius: 10,
    border: `1px solid ${theme.border}`,
    background: theme.surfaceAlt,
    color: theme.text,
    padding: '0 10px',
    fontSize: 14,
    outline: 'none',
  };
}

function labelStyle(theme) {
  return {
    fontSize: 11,
    fontWeight: 700,
    color: theme.textMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 4,
    display: 'block',
  };
}

export default function TypeFields({ theme, ticket, tables = [] }) {
  const { t } = useTranslation();
  const patchActive = usePosStore((s) => s.patchActive);
  const tickets = usePosStore((s) => s.tickets);

  if (!ticket) return null;

  // Tables occupied by other open tickets (compare by displayed table number)
  const occupied = new Set(
    tickets
      .filter((other) => other.id !== ticket.id && other.type === 'dinein' && other.table != null)
      .map((other) => String(other.table))
  );

  if (ticket.type === 'dinein') {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 90px', gap: 8 }}>
        <div>
          <label style={labelStyle(theme)}>{t('pos.single.table')}</label>
          <select
            value={ticket.table || ''}
            onChange={(e) => {
              const value = e.target.value;
              if (!value) {
                patchActive({ table: null, tableId: null });
                return;
              }
              const row = tables.find((r) => String(r.id) === value);
              patchActive({
                table: row?.tableNumber ?? row?.number ?? value,
                tableId: row?.id ?? null,
              });
            }}
            disabled={tables.length === 0}
            style={inputStyle(theme)}
          >
            <option value="">
              {tables.length === 0
                ? t('pos.single.noTablesConfigured')
                : t('pos.single.selectTable')}
            </option>
            {tables.map((row) => {
              const tid = String(row.id);
              const num = row.tableNumber ?? row.number ?? row.id;
              // Prefer the human-set table name; fall back to the
              // number so tables without a configured name still render.
              const display = row.tableName || t('pos.single.tableLabel', { n: num });
              const dis = occupied.has(String(num));
              return (
                <option key={tid} value={tid} disabled={dis}>
                  {display}
                  {dis ? ` ${t('pos.single.tableBusy')}` : ''}
                </option>
              );
            })}
          </select>
        </div>
        <div>
          <label style={labelStyle(theme)}>{t('pos.single.guests')}</label>
          <input
            type="number"
            min={1}
            max={20}
            value={ticket.guests || 1}
            onChange={(e) => patchActive({ guests: Number(e.target.value) || 1 })}
            style={{ ...inputStyle(theme), fontVariantNumeric: 'tabular-nums' }}
          />
        </div>
      </div>
    );
  }

  if (ticket.type === 'takeaway') {
    const c = ticket.customer || { name: '', phone: '' };
    const set = (partial) => patchActive({ customer: { ...c, ...partial } });
    return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
        <div>
          <label style={labelStyle(theme)}>{t('pos.single.name')}</label>
          <input
            value={c.name}
            onChange={(e) => set({ name: e.target.value })}
            placeholder={t('pos.single.namePh')}
            style={inputStyle(theme)}
          />
        </div>
        <div>
          <label style={labelStyle(theme)}>{t('pos.single.phone')}</label>
          <input
            value={c.phone}
            onChange={(e) => set({ phone: e.target.value })}
            placeholder={t('pos.single.phonePh')}
            style={inputStyle(theme)}
          />
        </div>
      </div>
    );
  }

  if (ticket.type === 'delivery') {
    const c = ticket.customer || { name: '', phone: '', address: '' };
    const set = (partial) => patchActive({ customer: { ...c, ...partial } });
    return (
      <div style={{ display: 'grid', gap: 8 }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <div>
            <label style={labelStyle(theme)}>{t('pos.single.name')}</label>
            <input
              value={c.name}
              onChange={(e) => set({ name: e.target.value })}
              placeholder={t('pos.single.namePh')}
              style={inputStyle(theme)}
            />
          </div>
          <div>
            <label style={labelStyle(theme)}>{t('pos.single.phone')}</label>
            <input
              value={c.phone}
              onChange={(e) => set({ phone: e.target.value })}
              placeholder={t('pos.single.phonePh')}
              style={inputStyle(theme)}
            />
          </div>
        </div>
        <div>
          <label style={labelStyle(theme)}>{t('pos.single.address')}</label>
          <input
            value={c.address || ''}
            onChange={(e) => set({ address: e.target.value })}
            placeholder={t('pos.single.addressPh')}
            style={inputStyle(theme)}
          />
        </div>
      </div>
    );
  }

  return null;
}
