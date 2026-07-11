/**
 * CENTRALIZED BRANDING CONFIGURATION
 *
 * Change these values to rebrand the entire platform.
 * This is the single source of truth for all branding across the application.
 *
 * Values can be overridden via Vite environment variables (VITE_BRAND_*).
 * For production, set these in your .env file or build environment.
 */

// Helper to get env var with fallback
const env = (key, fallback) => import.meta.env[key] || fallback;

const branding = {
  // ===== COMPANY/PLATFORM IDENTITY =====
  name: env('VITE_BRAND_NAME', 'Qahvoon'),
  shortName: env('VITE_BRAND_SHORT_NAME', 'Qahvoon'),
  tagline: env('VITE_BRAND_TAGLINE', 'Restaurant Delivery'),
  description: env('VITE_BRAND_DESCRIPTION', 'Restaurant Delivery Control Service'),

  // ===== CONTACT INFORMATION =====
  supportEmail: env('VITE_BRAND_SUPPORT_EMAIL', 'support@qahvoon.uz'),
  adminEmail: env('VITE_BRAND_ADMIN_EMAIL', 'admin@qahvoon.uz'),

  // ===== DOMAIN & URLs =====
  domain: env('VITE_BRAND_DOMAIN', 'qahvoon.uz'),
  apiUrl: env('VITE_BRAND_API_URL', 'https://www.qahvoon.uz'),
  websiteUrl: env('VITE_BRAND_WEBSITE_URL', 'https://www.qahvoon.uz'),

  // ===== DEMO CREDENTIALS (optional login-page hint; empty = no hint) =====
  // No defaults: the platform seeds no demo account (the operator is bootstrapped from
  // ADMIN_EMAIL/ADMIN_PASSWORD on first boot). Set the VITE_ vars only for a throwaway demo stand.
  demo: {
    email: env('VITE_BRAND_DEMO_EMAIL', ''),
    password: env('VITE_BRAND_DEMO_PASSWORD', ''),
  },

  // ===== VISUAL BRANDING =====
  colors: {
    primary: env('VITE_BRAND_COLOR_PRIMARY', '#2563eb'),
    primaryDark: env('VITE_BRAND_COLOR_PRIMARY_DARK', '#1d4ed8'),
    primaryLight: env('VITE_BRAND_COLOR_PRIMARY_LIGHT', '#3b82f6'),
    accent: env('VITE_BRAND_COLOR_ACCENT', '#f59e0b'),
  },

  // ===== LOGO PATHS (relative to public folder) =====
  logos: {
    main: env('VITE_BRAND_LOGO_MAIN', '/logo.svg'),
    icon: env('VITE_BRAND_LOGO_ICON', '/vite.svg'),
    light: env('VITE_BRAND_LOGO_LIGHT', '/logo-light.svg'),
    dark: env('VITE_BRAND_LOGO_DARK', '/logo-dark.svg'),
  },

  // ===== SOCIAL LINKS (optional) =====
  social: {
    facebook: env('VITE_BRAND_SOCIAL_FACEBOOK', ''),
    instagram: env('VITE_BRAND_SOCIAL_INSTAGRAM', ''),
    twitter: env('VITE_BRAND_SOCIAL_TWITTER', ''),
    telegram: env('VITE_BRAND_SOCIAL_TELEGRAM', ''),
  },

  // ===== LEGAL =====
  legal: {
    companyName: env('VITE_BRAND_COMPANY_NAME', 'Qahvoon'),
    copyrightYear: new Date().getFullYear(),
  },
};

// ===== DERIVED VALUES (auto-generated from above) =====
branding.fullName = `${branding.name} - ${branding.tagline}`;
branding.copyright = `© ${branding.legal.copyrightYear} ${branding.legal.companyName}`;
branding.demoCredentials = branding.demo.email ? `Demo: ${branding.demo.email} / ${branding.demo.password}` : '';

// Page titles
branding.titles = {
  admin: `${branding.name} - Control Panel`,
  order: `${branding.name} - Order Menu`,
  pos: `${branding.name} - Point of Sale`,
  kitchen: `${branding.name} - Kitchen Display`,
};

export default branding;

// Named exports for convenience
export const {
  name,
  shortName,
  tagline,
  description,
  fullName,
  supportEmail,
  adminEmail,
  domain,
  apiUrl,
  websiteUrl,
  demo,
  colors,
  logos,
  social,
  legal,
  copyright,
  demoCredentials,
  titles,
} = branding;
