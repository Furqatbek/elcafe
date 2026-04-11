import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { menuAPI } from '../../services/api';
import { UtensilsCrossed } from 'lucide-react';

const SLIDE_INTERVAL = 6000; // 6 seconds per slide

/**
 * CustomerAdsPanel — 70% left panel on the customer-facing display.
 * Rotates through real menu items from the database as full-screen images.
 * Handles: no products, no images, and broken image URLs gracefully.
 */
export default function CustomerAdsPanel({ restaurant, restaurantId }) {
  const { t } = useTranslation();
  const [currentSlide, setCurrentSlide] = useState(0);
  const [slides, setSlides] = useState([]);
  const [allProducts, setAllProducts] = useState([]);

  // Load menu items from the database
  const rid = restaurant?.id || restaurantId;
  useEffect(() => {
    if (!rid) return;
    menuAPI.getProductsByRestaurant(rid)
      .then((res) => {
        const products = res.data.data || res.data || [];
        setAllProducts(products);

        // Pick items with images, shuffle, take up to 8
        const withImages = products.filter(p => p.imageUrl);
        const shuffled = withImages.sort(() => Math.random() - 0.5).slice(0, 8);
        setSlides(shuffled.map(item => ({
          id: item.id,
          title: item.name,
          subtitle: item.description || '',
          price: item.price,
          image: item.imageUrl,
          imageFailed: false,
        })));
      })
      .catch((err) => console.error('Failed to load menu items for display:', err));
  }, [rid]);

  // Handle broken image — mark slide so it shows text-only fallback
  const handleImageError = useCallback((slideId) => {
    setSlides(prev => prev.map(s =>
      s.id === slideId ? { ...s, imageFailed: true } : s
    ));
  }, []);

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

  // No slides at all — show text-only fallback with product names from menu
  if (slides.length === 0) {
    return (
      <div className="h-full bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 flex flex-col items-center justify-center text-white p-12">
        <UtensilsCrossed className="w-20 h-20 text-gray-600 mb-8" />
        {allProducts.length > 0 ? (
          <div className="text-center space-y-4">
            <h2 className="text-3xl font-bold text-gray-300">
              {t('pos.customerDisplay.ourMenu', 'Our Menu')}
            </h2>
            <div className="flex flex-wrap justify-center gap-3 max-w-2xl">
              {allProducts.slice(0, 12).map((p) => (
                <span key={p.id} className="px-4 py-2 bg-white/10 rounded-full text-lg text-gray-300">
                  {p.name}
                </span>
              ))}
            </div>
          </div>
        ) : (
          <p className="text-2xl text-gray-500">
            {t('pos.customerDisplay.welcome', 'Welcome')}
          </p>
        )}
      </div>
    );
  }

  return (
    <div className="h-full relative overflow-hidden bg-black">
      {/* Rotating product slides */}
      {slides.map((slide, idx) => (
        <div
          key={slide.id}
          className={`absolute inset-0 transition-opacity duration-1000 ${
            idx === currentSlide ? 'opacity-100' : 'opacity-0'
          }`}
        >
          {/* Product image or text-only fallback if image broke */}
          {slide.image && !slide.imageFailed ? (
            <div className="w-full h-full flex items-center justify-center bg-black">
              <img
                src={slide.image}
                alt={slide.title}
                className="max-w-full max-h-full object-contain"
                onError={() => handleImageError(slide.id)}
              />
            </div>
          ) : (
            <div className="w-full h-full bg-gradient-to-br from-gray-900 via-gray-800 to-gray-900 flex items-center justify-center">
              <UtensilsCrossed className="w-32 h-32 text-gray-700" />
            </div>
          )}

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
