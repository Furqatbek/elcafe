import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { reservationPublicAPI } from '../../services/api';
import LanguageSelector from './LanguageSelector';
import {
  MapPin,
  Phone,
  Loader2,
  Calendar,
  ChevronRight,
} from 'lucide-react';

export default function RestaurantSelectPage() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [restaurants, setRestaurants] = useState([]);

  useEffect(() => {
    fetchRestaurants();
  }, []);

  const fetchRestaurants = async () => {
    try {
      setLoading(true);
      const response = await reservationPublicAPI.getReservableRestaurants();
      setRestaurants(response.data.data || []);
    } catch (err) {
      setError('Failed to load restaurants');
      console.error('Failed to fetch restaurants:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectRestaurant = (restaurantId) => {
    navigate(`/reserve/${restaurantId}`);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
          <p className="text-red-600">{t('customerReservation.loadError')}</p>
          <button
            onClick={fetchRestaurants}
            className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
          >
            {t('customerReservation.tryAgain')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-gradient-to-r from-blue-600 to-blue-700 text-white shadow-sm sticky top-0 z-10">
        <div className="max-w-lg mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Calendar className="h-6 w-6" />
              <h1 className="text-xl font-semibold">{t('customerReservation.title')}</h1>
            </div>
            <LanguageSelector />
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-lg mx-auto px-4 py-6">
        <p className="text-gray-600 mb-6">{t('customerReservation.selectRestaurant')}</p>

        {restaurants.length === 0 ? (
          <div className="bg-white rounded-lg shadow-lg p-6 text-center">
            <Calendar className="h-12 w-12 mx-auto mb-3 text-gray-300" />
            <p className="text-gray-600">{t('customerReservation.noRestaurants')}</p>
          </div>
        ) : (
          <div className="space-y-4">
            {restaurants.map((restaurant) => (
              <button
                key={restaurant.id}
                onClick={() => handleSelectRestaurant(restaurant.id)}
                className="w-full bg-white rounded-lg shadow-md p-4 text-left hover:shadow-lg transition-shadow border border-gray-100"
              >
                <div className="flex items-center gap-4">
                  {restaurant.logoUrl ? (
                    <img
                      src={restaurant.logoUrl}
                      alt={restaurant.name}
                      className="w-16 h-16 rounded-lg object-cover"
                    />
                  ) : (
                    <div className="w-16 h-16 rounded-lg bg-blue-100 flex items-center justify-center">
                      <Calendar className="h-8 w-8 text-blue-600" />
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <h3 className="font-semibold text-gray-800 truncate">{restaurant.name}</h3>
                    {restaurant.address && (
                      <p className="text-sm text-gray-500 flex items-center gap-1 mt-1">
                        <MapPin className="h-3 w-3 flex-shrink-0" />
                        <span className="truncate">{restaurant.address}</span>
                      </p>
                    )}
                    {restaurant.phone && (
                      <p className="text-sm text-gray-500 flex items-center gap-1 mt-1">
                        <Phone className="h-3 w-3 flex-shrink-0" />
                        <span>{restaurant.phone}</span>
                      </p>
                    )}
                  </div>
                  <ChevronRight className="h-5 w-5 text-gray-400 flex-shrink-0" />
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
