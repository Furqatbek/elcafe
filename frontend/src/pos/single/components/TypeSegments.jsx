import React from 'react';
import { useTranslation } from 'react-i18next';

const TYPES = [
  { key: 'dinein', i18nKey: 'pos.single.typeDinein' },
  { key: 'takeaway', i18nKey: 'pos.single.typeTakeaway' },
  { key: 'delivery', i18nKey: 'pos.single.typeDelivery' },
];

export default function TypeSegments({ theme, value, onChange }) {
  const { t } = useTranslation();
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
      {TYPES.map((seg) => {
        const active = value === seg.key;
        return (
          <button
            key={seg.key}
            onClick={() => onChange(seg.key)}
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
            {t(seg.i18nKey)}
          </button>
        );
      })}
    </div>
  );
}
