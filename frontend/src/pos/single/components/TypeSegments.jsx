import React from 'react';

const TYPES = [
  { key: 'dinein', label: 'Dine-in' },
  { key: 'takeaway', label: 'Takeaway' },
  { key: 'delivery', label: 'Delivery' },
];

export default function TypeSegments({ theme, value, onChange }) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr',
        gap: 0,
        background: theme.surfaceAlt,
        border: `1px solid ${theme.border}`,
        borderRadius: 10,
        padding: 4,
      }}
    >
      {TYPES.map((t) => {
        const active = value === t.key;
        return (
          <button
            key={t.key}
            onClick={() => onChange(t.key)}
            style={{
              height: 36,
              borderRadius: 8,
              border: 'none',
              background: active ? theme.surface : 'transparent',
              color: active ? theme.primary : theme.textMuted,
              fontWeight: 700,
              fontSize: 13,
              cursor: 'pointer',
              boxShadow: active ? '0 1px 2px rgba(0,0,0,.08)' : 'none',
            }}
          >
            {t.label}
          </button>
        );
      })}
    </div>
  );
}
