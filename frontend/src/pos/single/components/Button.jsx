import React from 'react';

const SIZES = {
  sm: { height: 32, padX: 10, font: 13 },
  md: { height: 40, padX: 14, font: 14 },
  lg: { height: 48, padX: 18, font: 15 },
  xl: { height: 56, padX: 22, font: 17 },
};

export default function Button({
  variant = 'default',
  size = 'md',
  theme,
  active = false,
  disabled = false,
  style = {},
  children,
  ...rest
}) {
  const s = SIZES[size] || SIZES.md;
  const t = theme;

  let bg = t.surface;
  let fg = t.text;
  let border = t.border;

  if (variant === 'primary') {
    bg = t.primary; fg = '#fff'; border = t.primary;
  } else if (variant === 'success') {
    bg = t.success; fg = '#fff'; border = t.success;
  } else if (variant === 'danger') {
    bg = t.danger; fg = '#fff'; border = t.danger;
  } else if (variant === 'ghost') {
    bg = 'transparent'; border = 'transparent';
  } else if (variant === 'soft') {
    bg = t.primarySoft; fg = t.primary; border = 'transparent';
  } else if (variant === 'outline') {
    bg = 'transparent'; border = t.border;
  }

  if (active) {
    bg = t.primary; fg = '#fff'; border = t.primary;
  }

  return (
    <button
      disabled={disabled}
      style={{
        height: s.height,
        padding: `0 ${s.padX}px`,
        fontSize: s.font,
        fontWeight: 600,
        borderRadius: 10,
        border: `1px solid ${border}`,
        background: bg,
        color: fg,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition: 'transform 80ms ease, background 120ms ease',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 6,
        whiteSpace: 'nowrap',
        userSelect: 'none',
        ...style,
      }}
      onMouseDown={(e) => {
        if (!disabled) e.currentTarget.style.transform = 'scale(0.97)';
      }}
      onMouseUp={(e) => {
        e.currentTarget.style.transform = 'scale(1)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'scale(1)';
      }}
      {...rest}
    >
      {children}
    </button>
  );
}
