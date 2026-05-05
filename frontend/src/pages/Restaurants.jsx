import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import { MapPin, Phone, Mail, Star, Clock, Plus, Edit, Trash2 } from 'lucide-react';

export default function Restaurants() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRestaurant, setEditingRestaurant] = useState(null);
  const [formData, setFormData] = useState({
    name: '', description: '', address: '', city: '', state: '', zipCode: '',
    phone: '', email: '', deliveryFee: '', estimatedDeliveryTimeMinutes: '30',
  });

  useEffect(() => {
    loadRestaurants();
  }, []);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 20, sort: 'name,asc' });
      setRestaurants(response.data.data?.content || response.data.data || []);
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = () => {
    setEditingRestaurant(null);
    setFormData({ name: '', description: '', address: '', city: '', state: '', zipCode: '', phone: '', email: '', deliveryFee: '', estimatedDeliveryTimeMinutes: '30' });
    setModalOpen(true);
  };

  const handleEdit = (restaurant) => {
    setEditingRestaurant(restaurant);
    setFormData({
      name: restaurant.name || '',
      description: restaurant.description || '',
      address: restaurant.address || '',
      city: restaurant.city || '',
      state: restaurant.state || '',
      zipCode: restaurant.zipCode || '',
      phone: restaurant.phone || '',
      email: restaurant.email || '',
      deliveryFee: restaurant.deliveryFee?.toString() || '',
      estimatedDeliveryTimeMinutes: restaurant.estimatedDeliveryTimeMinutes?.toString() || '30',
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const data = {
        ...formData,
        deliveryFee: formData.deliveryFee ? parseFloat(formData.deliveryFee) : null,
        estimatedDeliveryTimeMinutes: formData.estimatedDeliveryTimeMinutes ? parseInt(formData.estimatedDeliveryTimeMinutes) : null,
      };
      if (editingRestaurant) {
        await restaurantAPI.update(editingRestaurant.id, data);
      } else {
        await restaurantAPI.create(data);
      }
      setModalOpen(false);
      loadRestaurants();
    } catch (error) {
      console.error('Failed to save restaurant:', error);
      alert(error.response?.data?.message || 'Failed to save');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('restaurants.confirmDelete', 'Are you sure you want to delete this restaurant?'))) return;
    try {
      await restaurantAPI.delete(id);
      loadRestaurants();
    } catch (error) {
      console.error('Failed to delete:', error);
      alert(error.response?.data?.message || 'Failed to delete');
    }
  };

  if (loading) {
    return <div className="p-8">{t('common.loading')}</div>;
  }
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
        <Button onClick={handleCreate}>
          <Plus className="h-4 w-4 mr-2" />
          {t('restaurants.newRestaurant')}
        </Button>
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
                    <span className="font-medium">{restaurant.minimumOrderAmount?.toFixed(2)}</span>
                  </div>
                  <div className="flex justify-between text-sm mt-1">
                    <span className="text-muted-foreground">{t('restaurants.deliveryFee')}:</span>
                    <span className="font-medium">{restaurant.deliveryFee?.toFixed(2)}</span>
                  </div>
                </div>
                <div className="pt-2 flex gap-2">
                  <Button size="sm" variant="outline" className="flex-1" onClick={() => handleEdit(restaurant)}>
                    <Edit className="h-3 w-3 mr-1" />
                    {t('common.edit')}
                  </Button>
                  <Button size="sm" variant="outline" className="flex-1" onClick={() => navigate('/menu')}>
                    {t('restaurants.viewMenu')}
                  </Button>
                  <Button size="sm" variant="outline" className="text-red-600" onClick={() => handleDelete(restaurant.id)}>
                    <Trash2 className="h-3 w-3" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {/* Create/Edit Dialog */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {editingRestaurant ? t('restaurants.editRestaurant', 'Edit Restaurant') : t('restaurants.newRestaurant', 'New Restaurant')}
            </DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('restaurants.name', 'Name')} *</Label>
                <Input value={formData.name} onChange={(e) => setFormData({ ...formData, name: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('restaurants.phone', 'Phone')}</Label>
                <Input value={formData.phone} onChange={(e) => setFormData({ ...formData, phone: e.target.value })} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('restaurants.description', 'Description')}</Label>
              <Input value={formData.description} onChange={(e) => setFormData({ ...formData, description: e.target.value })} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('restaurants.address', 'Address')}</Label>
                <Input value={formData.address} onChange={(e) => setFormData({ ...formData, address: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('restaurants.city', 'City')}</Label>
                <Input value={formData.city} onChange={(e) => setFormData({ ...formData, city: e.target.value })} />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label>{t('restaurants.state', 'State')}</Label>
                <Input value={formData.state} onChange={(e) => setFormData({ ...formData, state: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('restaurants.zip', 'ZIP')}</Label>
                <Input value={formData.zipCode} onChange={(e) => setFormData({ ...formData, zipCode: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('restaurants.email', 'Email')}</Label>
                <Input value={formData.email} onChange={(e) => setFormData({ ...formData, email: e.target.value })} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('restaurants.deliveryFee', 'Delivery Fee')}</Label>
                <Input type="number" step="0.01" value={formData.deliveryFee} onChange={(e) => setFormData({ ...formData, deliveryFee: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>{t('restaurants.deliveryTime', 'Delivery Time (min)')}</Label>
                <Input type="number" value={formData.estimatedDeliveryTimeMinutes} onChange={(e) => setFormData({ ...formData, estimatedDeliveryTimeMinutes: e.target.value })} />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button onClick={handleSave} disabled={!formData.name}>{t('common.save', 'Save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
