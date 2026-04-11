import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

const SLIDE_INTERVAL = 6000; // 6 seconds per slide

/**
 * CustomerAdsPanel — 70% left panel on the customer-facing display.
 * Shows restaurant branding, logo, and rotating promotional slides.
 */
export default function CustomerAdsPanel({ restaurant }) {
  const { t } = useTranslation();
  const [currentSlide, setCurrentSlide] = useState(0);

  const slides = [
    {
      title: restaurant?.name || t('pos.customerDisplay.welcome', 'Welcome'),
      subtitle: restaurant?.address || '',
      type: 'welcome',
    },
    {
      title: t('pos.customerDisplay.freshFood', 'Freshly Prepared'),
      subtitle: t('pos.customerDisplay.freshFoodSub', 'Made with the finest ingredients'),
      type: 'promo',
    },
    {
      title: t('pos.customerDisplay.thankVisit', 'Thank you for visiting!'),
      subtitle: t('pos.customerDisplay.comeAgain', 'We hope to see you again soon'),
      type: 'thanks',
    },
  ];

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % slides.length);
    }, SLIDE_INTERVAL);
    return () => clearInterval(timer);
  }, [slides.length]);

  return (
    <div className="h-full relative overflow-hidden bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 flex flex-col items-center justify-center text-white">
      {/* Background pattern */}
      <div className="absolute inset-0 opacity-5">
        <div className="absolute inset-0" style={{
          backgroundImage: 'radial-gradient(circle at 25px 25px, white 2px, transparent 0)',
          backgroundSize: '50px 50px',
        }} />
      </div>

      {/* Logo */}
      <div className="relative z-10 flex flex-col items-center">
        {restaurant?.logoUrl ? (
          <img
            src={restaurant.logoUrl}
            alt={restaurant.name}
            className="w-40 h-40 object-contain mb-8 drop-shadow-2xl"
          />
        ) : (
          <div className="w-40 h-40 rounded-full bg-white/10 flex items-center justify-center mb-8 border-2 border-white/20">
            <span className="text-6xl font-bold text-white/80">
              {(restaurant?.name || 'R').charAt(0).toUpperCase()}
            </span>
          </div>
        )}

        {/* Restaurant name */}
        <h1 className="text-5xl font-bold mb-4 text-center px-8 drop-shadow-lg">
          {restaurant?.name || ''}
        </h1>

        {/* Rotating slides */}
        <div className="mt-8 h-24 flex items-center justify-center">
          {slides.map((slide, idx) => (
            <div
              key={idx}
              className={`absolute text-center transition-all duration-700 px-12 ${
                idx === currentSlide
                  ? 'opacity-100 translate-y-0'
                  : 'opacity-0 translate-y-4'
              }`}
            >
              <p className="text-2xl font-semibold text-blue-300">{slide.title}</p>
              {slide.subtitle && (
                <p className="text-lg text-gray-400 mt-2">{slide.subtitle}</p>
              )}
            </div>
          ))}
        </div>

        {/* Slide indicators */}
        <div className="flex gap-2 mt-12">
          {slides.map((_, idx) => (
            <div
              key={idx}
              className={`w-2.5 h-2.5 rounded-full transition-all duration-300 ${
                idx === currentSlide ? 'bg-blue-400 w-8' : 'bg-gray-600'
              }`}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
