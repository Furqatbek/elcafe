import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { menuAPI } from '../../services/api';

const SLIDE_INTERVAL = 6000; // 6 seconds per slide

/**
 * CustomerAdsPanel — 70% left panel on the customer-facing display.
 * Shows restaurant branding, logo, and rotating menu items from the database.
 */
export default function CustomerAdsPanel({ restaurant }) {
  const { t } = useTranslation();
  const [currentSlide, setCurrentSlide] = useState(0);
  const [menuItems, setMenuItems] = useState([]);

  // Load real menu items from the database
  useEffect(() => {
    if (!restaurant?.id) return;
    menuAPI.getProductsByRestaurant(restaurant.id)
      .then((res) => {
        const products = res.data.data || res.data || [];
        // Pick items that have images, shuffle and take up to 8
        const withImages = products.filter(p => p.imageUrl);
        const shuffled = withImages.sort(() => Math.random() - 0.5).slice(0, 8);
        setMenuItems(shuffled);
      })
      .catch((err) => console.error('Failed to load menu items for display:', err));
  }, [restaurant?.id]);

  // Build slides: welcome + menu items + thank you
  const slides = [
    {
      type: 'welcome',
      title: restaurant?.name || t('pos.customerDisplay.welcome', 'Welcome'),
      subtitle: restaurant?.address || '',
      image: null,
    },
    ...menuItems.map((item) => ({
      type: 'menu-item',
      title: item.name,
      subtitle: item.description || '',
      price: item.price,
      image: item.imageUrl,
    })),
    {
      type: 'thanks',
      title: t('pos.customerDisplay.thankVisit', 'Thank you for visiting!'),
      subtitle: t('pos.customerDisplay.comeAgain', 'We hope to see you again soon'),
      image: null,
    },
  ];

  useEffect(() => {
    if (slides.length <= 1) return;
    const timer = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % slides.length);
    }, SLIDE_INTERVAL);
    return () => clearInterval(timer);
  }, [slides.length]);

  const formatPrice = (n) => {
    if (n == null) return '';
    return Number(n).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 });
  };

  return (
    <div className="h-full relative overflow-hidden bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 flex flex-col items-center justify-center text-white">
      {/* Background pattern */}
      <div className="absolute inset-0 opacity-5">
        <div className="absolute inset-0" style={{
          backgroundImage: 'radial-gradient(circle at 25px 25px, white 2px, transparent 0)',
          backgroundSize: '50px 50px',
        }} />
      </div>

      {/* Logo — always visible */}
      <div className="relative z-10 flex flex-col items-center">
        {restaurant?.logoUrl ? (
          <img
            src={restaurant.logoUrl}
            alt={restaurant.name}
            className="w-32 h-32 object-contain mb-6 drop-shadow-2xl"
          />
        ) : (
          <div className="w-32 h-32 rounded-full bg-white/10 flex items-center justify-center mb-6 border-2 border-white/20">
            <span className="text-5xl font-bold text-white/80">
              {(restaurant?.name || 'R').charAt(0).toUpperCase()}
            </span>
          </div>
        )}

        <h1 className="text-4xl font-bold mb-8 text-center px-8 drop-shadow-lg">
          {restaurant?.name || ''}
        </h1>

        {/* Rotating slides */}
        <div className="relative w-full max-w-2xl h-80 flex items-center justify-center">
          {slides.map((slide, idx) => (
            <div
              key={idx}
              className={`absolute inset-0 flex flex-col items-center justify-center text-center px-8 transition-all duration-700 ${
                idx === currentSlide
                  ? 'opacity-100 scale-100'
                  : 'opacity-0 scale-95'
              }`}
            >
              {/* Menu item with image */}
              {slide.type === 'menu-item' && slide.image && (
                <div className="mb-4">
                  <img
                    src={slide.image}
                    alt={slide.title}
                    className="w-48 h-48 object-cover rounded-2xl shadow-2xl border-2 border-white/10"
                  />
                </div>
              )}

              <p className="text-3xl font-semibold text-blue-300">{slide.title}</p>

              {slide.subtitle && (
                <p className="text-lg text-gray-400 mt-2 line-clamp-2 max-w-md">{slide.subtitle}</p>
              )}

              {slide.price != null && (
                <p className="text-2xl font-bold text-white mt-3">{formatPrice(slide.price)}</p>
              )}
            </div>
          ))}
        </div>

        {/* Slide indicators */}
        {slides.length > 1 && (
          <div className="flex gap-2 mt-6">
            {slides.map((_, idx) => (
              <div
                key={idx}
                className={`h-2.5 rounded-full transition-all duration-300 ${
                  idx === currentSlide ? 'bg-blue-400 w-8' : 'bg-gray-600 w-2.5'
                }`}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
