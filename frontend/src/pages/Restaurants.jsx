import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { MapPin, Phone, Mail, Star, Clock } from 'lucide-react';

export default function Restaurants() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRestaurants();
  }, []);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 20, sort: 'name,asc' });
      setRestaurants(response.data.data.content || []);
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div>{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('restaurants.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('restaurants.allRestaurants')}
          </p>
        </div>
        <Button>{t('restaurants.newRestaurant')}</Button>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {restaurants.length === 0 ? (
          <Card className="col-span-full">
            <CardContent className="pt-6">
              <p className="text-center text-muted-foreground">
                {t('common.noData')}
              </p>
            </CardContent>
          </Card>
        ) : (
          restaurants.map((restaurant) => (
            <Card key={restaurant.id} className="hover:shadow-lg transition-shadow">
              {restaurant.bannerUrl && (
                <div className="h-32 overflow-hidden rounded-t-lg">
                  <img
                    src={restaurant.bannerUrl}
                    alt={restaurant.name}
                    className="w-full h-full object-cover"
                  />
                </div>
              )}
              <CardHeader>
                <div className="flex justify-between items-start">
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      {restaurant.logoUrl && (
                        <img
                          src={restaurant.logoUrl}
                          alt={restaurant.name}
                          className="w-10 h-10 rounded-full object-cover"
                        />
                      )}
                      <CardTitle>{restaurant.name}</CardTitle>
                    </div>
                    <CardDescription className="mt-2">
                      {restaurant.description}
                    </CardDescription>
                  </div>
                </div>
                <div className="flex gap-2 mt-2">
                  {restaurant.active ? (
                    <Badge variant="secondary" className="bg-green-100 text-green-800">
                      {t('restaurants.active')}
                    </Badge>
                  ) : (
                    <Badge variant="secondary" className="bg-gray-100 text-gray-800">
                      {t('restaurants.inactive')}
                    </Badge>
                  )}
                  {restaurant.acceptingOrders && (
                    <Badge className="bg-blue-100 text-blue-800">
                      {t('restaurants.acceptingOrders')}
                    </Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center text-sm text-muted-foreground">
                  <MapPin className="h-4 w-4 mr-2 flex-shrink-0" />
                  <span>{restaurant.address}, {restaurant.city}, {restaurant.state} {restaurant.zipCode}</span>
                </div>
                {restaurant.phone && (
                  <div className="flex items-center text-sm text-muted-foreground">
                    <Phone className="h-4 w-4 mr-2 flex-shrink-0" />
                    {restaurant.phone}
                  </div>
                )}
                {restaurant.email && (
                  <div className="flex items-center text-sm text-muted-foreground">
                    <Mail className="h-4 w-4 mr-2 flex-shrink-0" />
                    {restaurant.email}
                  </div>
                )}
                {restaurant.rating && (
                  <div className="flex items-center text-sm text-muted-foreground">
                    <Star className="h-4 w-4 mr-2 flex-shrink-0 fill-yellow-400 text-yellow-400" />
                    <span>{restaurant.rating.toFixed(1)} / 5.0</span>
                  </div>
                )}
                {restaurant.estimatedDeliveryTimeMinutes && (
                  <div className="flex items-center text-sm text-muted-foreground">
                    <Clock className="h-4 w-4 mr-2 flex-shrink-0" />
                    <span>{restaurant.estimatedDeliveryTimeMinutes} {t('restaurants.minutes')}</span>
                  </div>
                )}
                <div className="pt-2 border-t">
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">{t('restaurants.minimumOrder')}:</span>
                    <span className="font-medium">${restaurant.minimumOrderAmount?.toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between text-sm mt-1">
                    <span className="text-muted-foreground">{t('restaurants.deliveryFee')}:</span>
                    <span className="font-medium">${restaurant.deliveryFee?.toFixed(2)}</span>
                  </div>
                </div>
                <div className="pt-2 flex gap-2">
                  <Button size="sm" variant="outline" className="flex-1">
                    {t('common.edit')}
                  </Button>
                  <Button size="sm" variant="outline" className="flex-1">
                    {t('restaurants.viewMenu')}
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
