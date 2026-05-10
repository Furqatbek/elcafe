// Theme tokens — components consume these via the useTheme() hook,
// never hardcoding colors.

export const THEMES = {
  blue: {
    name: 'Blue',
    bg: '#f6f7fb',
    surface: '#ffffff',
    surfaceAlt: '#f3f4f6',
    primary: '#2563eb',
    primaryHover: '#1d4ed8',
    primarySoft: '#dbeafe',
    text: '#111827',
    textMuted: '#6b7280',
    border: '#e5e7eb',
    success: '#16a34a',
    successSoft: '#dcfce7',
    danger: '#dc2626',
    dangerSoft: '#fee2e2',
    warning: '#d97706',
    warningSoft: '#fef3c7',
    chip: '#eef2ff',
  },
  warm: {
    name: 'Warm',
    bg: '#faf6f1',
    surface: '#ffffff',
    surfaceAlt: '#f7efe5',
    primary: '#b8541b',
    primaryHover: '#963f10',
    primarySoft: '#fde7d3',
    text: '#2a1f14',
    textMuted: '#7a6650',
    border: '#ead9c2',
    success: '#3f6212',
    successSoft: '#ecfccb',
    danger: '#b91c1c',
    dangerSoft: '#fee2e2',
    warning: '#a16207',
    warningSoft: '#fef9c3',
    chip: '#f5e9d7',
  },
  dark: {
    name: 'Dark',
    bg: '#0b0f17',
    surface: '#131925',
    surfaceAlt: '#1a2231',
    primary: '#3b82f6',
    primaryHover: '#2563eb',
    primarySoft: '#1e3a8a',
    text: '#f8fafc',
    textMuted: '#94a3b8',
    border: '#26303f',
    success: '#22c55e',
    successSoft: '#14532d',
    danger: '#ef4444',
    dangerSoft: '#7f1d1d',
    warning: '#f59e0b',
    warningSoft: '#78350f',
    chip: '#1e293b',
  },
};

export const CATEGORY_COLORS = {
  coffee: '#7c3a1d',
  tea: '#0f5132',
  cold: '#1d4ed8',
  bakery: '#f3d6a8',
  mains: '#b91c1c',
  desserts: '#a855f7',
};

const CATEGORY_FALLBACK = ['#2563eb', '#7c3a1d', '#0f5132', '#a855f7', '#b91c1c', '#f59e0b', '#0891b2', '#be185d'];

export function categoryColorFor(category, idx = 0) {
  if (!category) return '#9ca3af';
  const slug = String(category.slug || category.name || '').toLowerCase();
  for (const key of Object.keys(CATEGORY_COLORS)) {
    if (slug.includes(key)) return CATEGORY_COLORS[key];
  }
  if (category.color) return category.color;
  return CATEGORY_FALLBACK[idx % CATEGORY_FALLBACK.length];
}

export const TYPE_CHIP = {
  dinein: { code: 'DI', label: 'Dine-in' },
  takeaway: { code: 'TA', label: 'Takeaway' },
  delivery: { code: 'DL', label: 'Delivery' },
};

export const TYPE_CHIP_COLORS = {
  blue: {
    dinein: { bg: '#dbeafe', fg: '#1e40af' },
    takeaway: { bg: '#fef3c7', fg: '#92400e' },
    delivery: { bg: '#dcfce7', fg: '#166534' },
  },
  warm: {
    dinein: { bg: '#fde7d3', fg: '#9a3412' },
    takeaway: { bg: '#fef9c3', fg: '#854d0e' },
    delivery: { bg: '#dcfce7', fg: '#166534' },
  },
  dark: {
    dinein: { bg: '#1e3a8a', fg: '#bfdbfe' },
    takeaway: { bg: '#78350f', fg: '#fde68a' },
    delivery: { bg: '#14532d', fg: '#86efac' },
  },
};

export function fmtMoney(n) {
  const v = Math.round(Number(n) || 0);
  return v.toLocaleString('en-US');
}

export const MONEY_STYLE = { fontVariantNumeric: 'tabular-nums' };
