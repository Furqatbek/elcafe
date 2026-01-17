import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Globe, Check } from 'lucide-react';

const languages = [
  { code: 'en', name: 'English', flag: '🇬🇧' },
  { code: 'ru', name: 'Русский', flag: '🇷🇺' },
  { code: 'uz', name: "O'zbek", flag: '🇺🇿' },
];

export default function LanguageSelector({ variant = 'dropdown' }) {
  const { i18n } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);

  const currentLang = languages.find(l => l.code === i18n.language) || languages[0];

  const changeLanguage = (langCode) => {
    i18n.changeLanguage(langCode);
    localStorage.setItem('language', langCode);
    setIsOpen(false);
  };

  if (variant === 'buttons') {
    return (
      <div className="flex gap-1 bg-white/10 rounded-lg p-1">
        {languages.map((lang) => (
          <button
            key={lang.code}
            onClick={() => changeLanguage(lang.code)}
            className={`px-3 py-1.5 rounded-md text-sm font-medium transition-colors ${
              i18n.language === lang.code
                ? 'bg-white text-gray-900'
                : 'text-white hover:bg-white/20'
            }`}
          >
            {lang.flag} {lang.code.toUpperCase()}
          </button>
        ))}
      </div>
    );
  }

  return (
    <div className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2 px-3 py-2 rounded-lg bg-white/10 hover:bg-white/20 transition-colors text-white"
      >
        <Globe className="w-4 h-4" />
        <span className="text-sm">{currentLang.flag} {currentLang.code.toUpperCase()}</span>
      </button>

      {isOpen && (
        <>
          <div
            className="fixed inset-0 z-40"
            onClick={() => setIsOpen(false)}
          />
          <div className="absolute right-0 mt-2 w-40 bg-white rounded-lg shadow-lg border z-50 overflow-hidden">
            {languages.map((lang) => (
              <button
                key={lang.code}
                onClick={() => changeLanguage(lang.code)}
                className="w-full flex items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors"
              >
                <span className="flex items-center gap-2">
                  <span>{lang.flag}</span>
                  <span className="text-sm text-gray-700">{lang.name}</span>
                </span>
                {i18n.language === lang.code && (
                  <Check className="w-4 h-4 text-green-600" />
                )}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
