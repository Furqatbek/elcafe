import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import ru from './locales/ru.json';
import uz from './locales/uz.json';
import branding from '../config/branding';

// Inject branding values into all locales
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

i18n
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: injectBranding(en) },
      ru: { translation: injectBranding(ru) },
      uz: { translation: injectBranding(uz) },
    },
    lng: localStorage.getItem('language') || 'en',
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false,
    },
  });

export default i18n;
