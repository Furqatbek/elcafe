import React from 'react';
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
  const patchActive = usePosStore((s) => s.patchActive);
  const tickets = usePosStore((s) => s.tickets);

  if (!ticket) return null;

  // Tables occupied by other tickets
  const occupied = new Set(
    tickets
      .filter((t) => t.id !== ticket.id && t.type === 'dinein' && t.table)
      .map((t) => String(t.table))
  );

  if (ticket.type === 'dinein') {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 90px', gap: 8 }}>
        <div>
          <label style={labelStyle(theme)}>Table</label>
          <select
            value={ticket.table || ''}
            onChange={(e) => patchActive({ table: e.target.value || null })}
            style={inputStyle(theme)}
          >
            <option value="">Select table…</option>
            {tables.map((t) => {
              const tid = String(t.id ?? t.tableNumber ?? t.number);
              const num = t.tableNumber ?? t.number ?? t.id;
              const dis = occupied.has(tid);
              return (
                <option key={tid} value={tid} disabled={dis}>
                  Table {num}{dis ? ' (busy)' : ''}
                </option>
              );
            })}
            {tables.length === 0 &&
              [1, 2, 3, 4, 5, 6, 7, 8].map((n) => {
                const tid = String(n);
                const dis = occupied.has(tid);
                return (
                  <option key={n} value={tid} disabled={dis}>
                    Table {n}{dis ? ' (busy)' : ''}
                  </option>
                );
              })}
          </select>
        </div>
        <div>
          <label style={labelStyle(theme)}>Guests</label>
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
          <label style={labelStyle(theme)}>Name</label>
          <input
            value={c.name}
            onChange={(e) => set({ name: e.target.value })}
            placeholder="Customer name"
            style={inputStyle(theme)}
          />
        </div>
        <div>
          <label style={labelStyle(theme)}>Phone</label>
          <input
            value={c.phone}
            onChange={(e) => set({ phone: e.target.value })}
            placeholder="Phone"
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
            <label style={labelStyle(theme)}>Name</label>
            <input
              value={c.name}
              onChange={(e) => set({ name: e.target.value })}
              placeholder="Customer name"
              style={inputStyle(theme)}
            />
          </div>
          <div>
            <label style={labelStyle(theme)}>Phone</label>
            <input
              value={c.phone}
              onChange={(e) => set({ phone: e.target.value })}
              placeholder="Phone"
              style={inputStyle(theme)}
            />
          </div>
        </div>
        <div>
          <label style={labelStyle(theme)}>Address</label>
          <input
            value={c.address || ''}
            onChange={(e) => set({ address: e.target.value })}
            placeholder="Delivery address"
            style={inputStyle(theme)}
          />
        </div>
      </div>
    );
  }

  return null;
}
