/**
 * POS Design System Configuration
 * Touch-optimized design tokens for tablet POS interface
 */

export const designSystem = {
  // Color Palette
  colors: {
    primary: {
      main: '#2563EB',      // Blue 600
      hover: '#1D4ED8',     // Blue 700
      active: '#1E40AF',    // Blue 800
      light: '#DBEAFE',     // Blue 100
      dark: '#1E3A8A',      // Blue 900
    },
    success: {
      main: '#10B981',      // Green 500
      hover: '#059669',     // Green 600
      light: '#D1FAE5',     // Green 100
    },
    warning: {
      main: '#F59E0B',      // Amber 500
      hover: '#D97706',     // Amber 600
      light: '#FEF3C7',     // Amber 100
    },
    danger: {
      main: '#EF4444',      // Red 500
      hover: '#DC2626',     // Red 600
      light: '#FEE2E2',     // Red 100
    },
    neutral: {
      50: '#FAFAFA',
      100: '#F5F5F5',
      200: '#E5E5E5',
      300: '#D4D4D4',
      400: '#A3A3A3',
      500: '#737373',
      600: '#525252',
      700: '#404040',
      800: '#262626',
      900: '#171717',
    },
    background: {
      primary: '#FFFFFF',
      secondary: '#F9FAFB',
      tertiary: '#F3F4F6',
    },
  },

  // Typography
  typography: {
    fontFamily: {
      base: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
      mono: '"SF Mono", Monaco, "Cascadia Code", "Roboto Mono", Consolas, monospace',
    },
    fontSize: {
      xs: '0.75rem',     // 12px - Small labels
      sm: '0.875rem',    // 14px - Body text
      base: '1rem',      // 16px - Default
      lg: '1.125rem',    // 18px - Emphasized text
      xl: '1.25rem',     // 20px - Small headings
      '2xl': '1.5rem',   // 24px - Section headings
      '3xl': '1.875rem', // 30px - Page titles
      '4xl': '2.25rem',  // 36px - Large displays
      '5xl': '3rem',     // 48px - Price displays
      '6xl': '3.75rem',  // 60px - Hero numbers
    },
    fontWeight: {
      normal: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
    },
    lineHeight: {
      tight: 1.2,
      normal: 1.5,
      relaxed: 1.75,
    },
  },

  // Spacing Scale (based on 4px grid)
  spacing: {
    0: '0',
    1: '0.25rem',   // 4px
    2: '0.5rem',    // 8px
    3: '0.75rem',   // 12px
    4: '1rem',      // 16px
    5: '1.25rem',   // 20px
    6: '1.5rem',    // 24px
    8: '2rem',      // 32px
    10: '2.5rem',   // 40px
    12: '3rem',     // 48px
    16: '4rem',     // 64px
    20: '5rem',     // 80px
    24: '6rem',     // 96px
  },

  // Touch Targets (minimum 48x48px for accessibility)
  touchTargets: {
    small: {
      minHeight: '48px',
      minWidth: '48px',
      padding: '12px',
    },
    medium: {
      minHeight: '56px',
      minWidth: '56px',
      padding: '16px',
    },
    large: {
      minHeight: '64px',
      minWidth: '64px',
      padding: '20px',
    },
    xl: {
      minHeight: '80px',
      minWidth: '80px',
      padding: '24px',
    },
  },

  // Border Radius
  borderRadius: {
    none: '0',
    sm: '0.25rem',   // 4px
    base: '0.5rem',  // 8px
    md: '0.75rem',   // 12px
    lg: '1rem',      // 16px
    xl: '1.5rem',    // 24px
    full: '9999px',
  },

  // Shadows
  shadows: {
    sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
    base: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1)',
    md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)',
    lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
    inner: 'inset 0 2px 4px 0 rgba(0, 0, 0, 0.05)',
  },

  // Transitions
  transitions: {
    fast: '150ms cubic-bezier(0.4, 0, 0.2, 1)',
    base: '200ms cubic-bezier(0.4, 0, 0.2, 1)',
    slow: '300ms cubic-bezier(0.4, 0, 0.2, 1)',
  },

  // Z-Index Scale
  zIndex: {
    base: 0,
    dropdown: 1000,
    sticky: 1100,
    fixed: 1200,
    modal: 1300,
    popover: 1400,
    tooltip: 1500,
  },

  // Grid System
  grid: {
    columns: 12,
    gutter: '1.5rem', // 24px
    maxWidth: '1280px', // Tablet landscape max
  },

  // Breakpoints (for responsive POS on different tablets)
  breakpoints: {
    sm: '640px',   // Small tablets portrait
    md: '768px',   // Standard tablets portrait
    lg: '1024px',  // Tablets landscape
    xl: '1280px',  // Large tablets landscape
  },
};

export default designSystem;
