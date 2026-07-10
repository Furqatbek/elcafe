import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import branding from '../config/branding';

// Inject branding values into a locale.
const injectBranding = (locale) => ({
  ...locale,
  app: {
    ...locale.app,
    name: branding.fullName,
    brandName: branding.name,
    tagline: branding.tagline,
  },
  auth: {
    ...locale.auth,
    demoCredentials: branding.demoCredentials,
  },
  branding: {
    name: branding.name,
    shortName: branding.shortName,
    tagline: branding.tagline,
    fullName: branding.fullName,
    supportEmail: branding.supportEmail,
    copyright: branding.copyright,
    demoEmail: branding.demo.email,
    demoPassword: branding.demo.password,
  },
});

// Each locale is a dynamic import → its own chunk, fetched only when its language is actually used, so
// the initial download is one language (+ the en fallback) instead of all three (~440 KB / ~150 KB gzip
// less). Locales load on demand when the user switches languages (see the languageChanged handler).
const loaders = {
  en: () => import('./locales/en.json'),
  ru: () => import('./locales/ru.json'),
  uz: () => import('./locales/uz.json'),
};
const loaded = new Set();

async function loadLanguage(lng) {
  if (loaded.has(lng) || !loaders[lng]) return;
  try {
    const mod = await loaders[lng]();
    i18n.addResourceBundle(lng, 'translation', injectBranding(mod.default), true, true);
    loaded.add(lng);
  } catch (e) {
    // Non-fatal: a missing bundle just means that language falls back to en (or raw keys) rather than
    // blanking the app. The initial render is gated on `ready`, not on any single bundle.
    console.error(`[i18n] failed to load locale "${lng}"`, e);
  }
}

const initial = localStorage.getItem('language') || 'en';

/**
 * Resolves once i18n is initialized with the active language and the en fallback loaded — the entry
 * points await this before the first render so there is no flash of untranslated (raw-key) content.
 */
export const ready = i18n
  .use(initReactI18next)
  .init({
    resources: {},                 // filled on demand by addResourceBundle
    lng: initial,
    fallbackLng: 'en',
    interpolation: { escapeValue: false },
    // Re-render react-i18next consumers when a bundle is added (so a language switched to before its
    // chunk finished loading updates as soon as it arrives), not only on languageChanged.
    react: { bindI18n: 'languageChanged loaded added' },
  })
  .then(async () => {
    await loadLanguage('en');       // the fallback must always be present
    if (initial !== 'en') await loadLanguage(initial);
  });

// Fetch a language's bundle on demand the first time the user switches to it.
i18n.on('languageChanged', (lng) => { loadLanguage(lng); });

export default i18n;
