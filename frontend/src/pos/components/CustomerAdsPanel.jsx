import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { menuAPI } from '../../services/api';

const SLIDE_INTERVAL = 6000; // 6 seconds per slide

/**
 * CustomerAdsPanel — 70% left panel on the customer-facing display.
 * Rotates through real menu items from the database as full-screen images.
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
        const withImages = products.filter(p => p.imageUrl);
        const shuffled = withImages.sort(() => Math.random() - 0.5).slice(0, 8);
        setMenuItems(shuffled);
      })
      .catch((err) => console.error('Failed to load menu items for display:', err));
  }, [restaurant?.id]);

  // Build slides from menu items only (no welcome/thanks text slides)
  const slides = menuItems.map((item) => ({
    title: item.name,
    subtitle: item.description || '',
    price: item.price,
    image: item.imageUrl,
  }));

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

  // No menu items loaded yet — show minimal dark background
  if (slides.length === 0) {
    return (
      <div className="h-full bg-gray-900" />
    );
  }

  return (
    <div className="h-full relative overflow-hidden bg-gray-900">
      {/* Full-bleed rotating product images */}
      {slides.map((slide, idx) => (
        <div
          key={idx}
          className={`absolute inset-0 transition-opacity duration-1000 ${
            idx === currentSlide ? 'opacity-100' : 'opacity-0'
          }`}
        >
          {/* Product image — fills entire block */}
          <img
            src={slide.image}
            alt={slide.title}
            className="w-full h-full object-cover"
          />

          {/* Dark gradient overlay at bottom for text readability */}
          <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent" />

          {/* Product info overlay at bottom */}
          <div className="absolute bottom-0 left-0 right-0 p-8">
            <h2 className="text-4xl font-bold text-white drop-shadow-lg">{slide.title}</h2>
            {slide.subtitle && (
              <p className="text-xl text-gray-300 mt-2 line-clamp-2 max-w-2xl">{slide.subtitle}</p>
            )}
            {slide.price != null && (
              <p className="text-3xl font-bold text-white mt-3">{formatPrice(slide.price)}</p>
            )}
          </div>
        </div>
      ))}

      {/* Slide indicators */}
      {slides.length > 1 && (
        <div className="absolute bottom-4 right-8 flex gap-2 z-10">
          {slides.map((_, idx) => (
            <div
              key={idx}
              className={`h-2 rounded-full transition-all duration-300 ${
                idx === currentSlide ? 'bg-white w-8' : 'bg-white/40 w-2'
              }`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
