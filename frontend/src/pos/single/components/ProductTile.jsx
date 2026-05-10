import React from 'react';
import { fmtMoney, MONEY_STYLE } from '../theme';

const DENSITY = {
  compact: { minHeight: 84, font: 13, padTop: 10 },
  balanced: { minHeight: 110, font: 14, padTop: 12 },
  spacious: { minHeight: 140, font: 16, padTop: 16 },
};

export default function ProductTile({
  theme,
  product,
  color,
  qty,
  hasOptions,
  density,
  burst,
  onClick,
}) {
  const d = DENSITY[density] || DENSITY.balanced;

  return (
    <button
      onClick={onClick}
      style={{
        position: 'relative',
        minHeight: d.minHeight,
        background: theme.surface,
        border: `1px solid ${theme.border}`,
        borderRadius: 12,
        cursor: 'pointer',
        textAlign: 'left',
        padding: `${d.padTop}px 12px 10px 18px`,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        overflow: 'hidden',
        transition: 'transform 80ms ease, border-color 120ms ease',
      }}
      onMouseDown={(e) => (e.currentTarget.style.transform = 'scale(0.97)')}
      onMouseUp={(e) => (e.currentTarget.style.transform = 'scale(1)')}
      onMouseLeave={(e) => (e.currentTarget.style.transform = 'scale(1)')}
    >
      {/* Left stripe */}
      <span
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: 6,
          background: color,
        }}
      />

      <span
        style={{
          fontSize: d.font,
          fontWeight: 600,
          color: theme.text,
          lineHeight: 1.25,
          textWrap: 'pretty',
          overflow: 'hidden',
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
        }}
      >
        {product.name}
      </span>

      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between' }}>
        <span
          style={{
            fontSize: d.font,
            fontWeight: 700,
            color: theme.text,
            ...MONEY_STYLE,
          }}
        >
          {fmtMoney(product.price)}
        </span>
        {hasOptions && (
          <span
            style={{
              fontSize: 10,
              fontWeight: 800,
              letterSpacing: 0.5,
              color: theme.textMuted,
              background: theme.surfaceAlt,
              padding: '2px 5px',
              borderRadius: 4,
              border: `1px solid ${theme.border}`,
            }}
          >
            OPT
          </span>
        )}
      </div>

      {qty > 0 && (
        <span
          style={{
            position: 'absolute',
            top: 6,
            right: 6,
            minWidth: 22,
            height: 22,
            padding: '0 6px',
            borderRadius: 999,
            background: theme.primary,
            color: '#fff',
            fontSize: 12,
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            ...MONEY_STYLE,
            transform: burst ? 'scale(1.25)' : 'scale(1)',
            transition: 'transform 220ms cubic-bezier(.34,1.56,.64,1)',
          }}
        >
          {qty}
        </span>
      )}
    </button>
  );
}
