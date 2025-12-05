import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { MapContainer, TileLayer, Marker, Popup, Circle } from 'react-leaflet';
import { MapPin, Navigation, Clock, User, RefreshCw, Circle as CircleIcon } from 'lucide-react';
import { courierAPI } from '../services/api';
import { Button } from '../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Badge } from '../components/ui/badge';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';

// Fix for default marker icons in React-Leaflet
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
});

// Custom marker icons for different statuses
const createCustomIcon = (color) => {
  return L.divIcon({
    className: 'custom-div-icon',
    html: `<div style="background-color: ${color}; width: 30px; height: 30px; border-radius: 50%; border: 3px solid white; box-shadow: 0 2px 5px rgba(0,0,0,0.3);"></div>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });
};

const statusIcons = {
  ONLINE: createCustomIcon('#22c55e'),
  ON_DELIVERY: createCustomIcon('#3b82f6'),
  BUSY: createCustomIcon('#eab308'),
  OFFLINE: createCustomIcon('#9ca3af'),
};

/**
 * Courier Map - Real-time courier tracking dashboard with interactive map
 */
export default function CourierMap() {
  const { t } = useTranslation();
  const [couriers, setCouriers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [lastUpdate, setLastUpdate] = useState(new Date());
  const [stats, setStats] = useState({
    online: 0,
    offline: 0,
    onDelivery: 0,
    busy: 0
  });

  // Default map center (Tashkent, Uzbekistan)
  const defaultCenter = [41.2995, 69.2401];
  const [mapCenter, setMapCenter] = useState(defaultCenter);

  // Fetch all couriers with their status
  const fetchCouriers = async () => {
    try {
      setLoading(true);
      const response = await courierAPI.getAll(0, 100);
      const courierList = response.data.data.content;

      // Fetch status for each courier
      const couriersWithStatus = await Promise.all(
        courierList.map(async (courier) => {
          try {
            const statusResponse = await courierAPI.getStatus(courier.id);
            return {
              ...courier,
              status: statusResponse.data.data
            };
          } catch (error) {
            return {
              ...courier,
              status: {
                isOnline: false,
                currentStatus: 'OFFLINE',
                lastSeenAt: null
              }
            };
          }
        })
      );

      // Filter couriers with location data for map
      const couriersWithLocation = couriersWithStatus.filter(
        c => c.status?.latitude && c.status?.longitude
      );

      // Update map center to first online courier with location
      if (couriersWithLocation.length > 0) {
        const firstOnline = couriersWithLocation.find(c => c.status?.isOnline);
        if (firstOnline) {
          setMapCenter([firstOnline.status.latitude, firstOnline.status.longitude]);
        }
      }

      setCouriers(couriersWithStatus);

      // Calculate stats
      const newStats = {
        online: 0,
        offline: 0,
        onDelivery: 0,
        busy: 0
      };

      couriersWithStatus.forEach((courier) => {
        const status = courier.status?.currentStatus || 'OFFLINE';
        if (status === 'ONLINE') newStats.online++;
        else if (status === 'OFFLINE') newStats.offline++;
        else if (status === 'ON_DELIVERY') newStats.onDelivery++;
        else if (status === 'BUSY') newStats.busy++;
      });

      setStats(newStats);
      setLastUpdate(new Date());
    } catch (error) {
      console.error('Error fetching couriers:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCouriers();

    // Auto-refresh every 10 seconds
    const interval = setInterval(fetchCouriers, 10000);
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = (status) => {
    switch (status) {
      case 'ONLINE':
        return 'bg-green-500';
      case 'ON_DELIVERY':
        return 'bg-blue-500';
      case 'BUSY':
        return 'bg-yellow-500';
      case 'OFFLINE':
      default:
        return 'bg-gray-400';
    }
  };

  const getStatusBadgeVariant = (status) => {
    switch (status) {
      case 'ONLINE':
        return 'default';
      case 'ON_DELIVERY':
        return 'secondary';
      case 'BUSY':
        return 'outline';
      case 'OFFLINE':
      default:
        return 'destructive';
    }
  };

  const getStatusLabel = (status) => {
    const labels = {
      ONLINE: t('courier.status.online', 'Online'),
      OFFLINE: t('courier.status.offline', 'Offline'),
      ON_DELIVERY: t('courier.status.onDelivery', 'On Delivery'),
      BUSY: t('courier.status.busy', 'Busy')
    };
    return labels[status] || status;
  };

  const formatLastSeen = (lastSeenAt) => {
    if (!lastSeenAt) return t('courier.map.neverSeen', 'Never');

    const diff = Date.now() - new Date(lastSeenAt).getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(minutes / 60);

    if (minutes < 1) return t('courier.map.justNow', 'Just now');
    if (minutes < 60) return t('courier.map.minutesAgo', { minutes }, `${minutes}m ago`);
    if (hours < 24) return t('courier.map.hoursAgo', { hours }, `${hours}h ago`);
    return new Date(lastSeenAt).toLocaleDateString();
  };

  // Filter couriers with valid location data
  const couriersOnMap = couriers.filter(
    c => c.status?.latitude && c.status?.longitude
  );

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">
            {t('courier.map.title', 'Courier Map')}
          </h1>
          <p className="text-gray-500">
            {t('courier.map.subtitle', 'Real-time courier tracking and status monitoring')}
          </p>
        </div>
        <Button onClick={fetchCouriers} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          {t('common.refresh', 'Refresh')}
        </Button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              {t('courier.map.stats.online', 'Online')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center space-x-2">
              <CircleIcon className="h-3 w-3 fill-green-500 text-green-500" />
              <span className="text-2xl font-bold">{stats.online}</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              {t('courier.map.stats.onDelivery', 'On Delivery')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center space-x-2">
              <CircleIcon className="h-3 w-3 fill-blue-500 text-blue-500" />
              <span className="text-2xl font-bold">{stats.onDelivery}</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              {t('courier.map.stats.busy', 'Busy')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center space-x-2">
              <CircleIcon className="h-3 w-3 fill-yellow-500 text-yellow-500" />
              <span className="text-2xl font-bold">{stats.busy}</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-gray-500">
              {t('courier.map.stats.offline', 'Offline')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center space-x-2">
              <CircleIcon className="h-3 w-3 fill-gray-400 text-gray-400" />
              <span className="text-2xl font-bold">{stats.offline}</span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Last Update Info */}
      <div className="flex items-center text-sm text-gray-500">
        <Clock className="mr-2 h-4 w-4" />
        {t('courier.map.lastUpdate', 'Last updated')}: {lastUpdate.toLocaleTimeString()}
      </div>

      {/* Interactive Map */}
      <Card>
        <CardContent className="p-0">
          <div style={{ height: '500px', width: '100%' }}>
            <MapContainer
              center={mapCenter}
              zoom={12}
              style={{ height: '100%', width: '100%', borderRadius: '0.5rem' }}
            >
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
              />

              {couriersOnMap.map((courier) => (
                <Marker
                  key={courier.id}
                  position={[courier.status.latitude, courier.status.longitude]}
                  icon={statusIcons[courier.status.currentStatus] || statusIcons.OFFLINE}
                >
                  <Popup>
                    <div className="p-2">
                      <div className="font-semibold text-lg mb-2">
                        {courier.firstName} {courier.lastName}
                      </div>
                      <div className="space-y-1 text-sm">
                        <div className="flex items-center gap-2">
                          <Badge variant={getStatusBadgeVariant(courier.status.currentStatus)}>
                            {getStatusLabel(courier.status.currentStatus)}
                          </Badge>
                        </div>
                        <div className="flex items-center gap-2">
                          <Navigation className="h-3 w-3" />
                          <span>{courier.vehicle}</span>
                        </div>
                        {courier.phone && (
                          <div className="flex items-center gap-2">
                            <span>📞</span>
                            <span>{courier.phone}</span>
                          </div>
                        )}
                        <div className="flex items-center gap-2">
                          <Clock className="h-3 w-3" />
                          <span>{formatLastSeen(courier.status.lastSeenAt)}</span>
                        </div>
                      </div>
                    </div>
                  </Popup>

                  {/* Accuracy circle if available */}
                  {courier.status.accuracy && (
                    <Circle
                      center={[courier.status.latitude, courier.status.longitude]}
                      radius={courier.status.accuracy}
                      pathOptions={{
                        fillColor: '#3b82f6',
                        fillOpacity: 0.1,
                        color: '#3b82f6',
                        weight: 1
                      }}
                    />
                  )}
                </Marker>
              ))}
            </MapContainer>
          </div>
        </CardContent>
      </Card>

      {/* Couriers List */}
      <div>
        <h2 className="text-xl font-semibold mb-4">
          {t('courier.map.couriersList', 'All Couriers')} ({couriers.length})
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {loading ? (
            <div className="col-span-full text-center py-12">
              <RefreshCw className="h-8 w-8 animate-spin mx-auto mb-2" />
              <p className="text-gray-500">{t('common.loading', 'Loading')}...</p>
            </div>
          ) : couriers.length === 0 ? (
            <div className="col-span-full text-center py-12">
              <User className="h-12 w-12 mx-auto mb-2 text-gray-400" />
              <p className="text-gray-500">{t('courier.map.noCouriers', 'No couriers found')}</p>
            </div>
          ) : (
            couriers.map((courier) => (
              <Card
                key={courier.id}
                className="hover:shadow-lg transition-shadow cursor-pointer"
                onClick={() => {
                  if (courier.status?.latitude && courier.status?.longitude) {
                    setMapCenter([courier.status.latitude, courier.status.longitude]);
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  }
                }}
              >
                <CardContent className="p-4">
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center space-x-3">
                      <div className="relative">
                        <div className="w-12 h-12 rounded-full bg-gray-200 flex items-center justify-center">
                          <User className="h-6 w-6 text-gray-500" />
                        </div>
                        <div className={`absolute -bottom-1 -right-1 w-4 h-4 rounded-full border-2 border-white ${getStatusColor(courier.status?.currentStatus || 'OFFLINE')}`} />
                      </div>
                      <div>
                        <h3 className="font-semibold">
                          {courier.firstName} {courier.lastName}
                        </h3>
                        <p className="text-sm text-gray-500">{courier.vehicle}</p>
                      </div>
                    </div>
                    <Badge variant={getStatusBadgeVariant(courier.status?.currentStatus || 'OFFLINE')}>
                      {getStatusLabel(courier.status?.currentStatus || 'OFFLINE')}
                    </Badge>
                  </div>

                  <div className="space-y-2 text-sm">
                    <div className="flex items-center text-gray-600">
                      <Clock className="h-4 w-4 mr-2" />
                      <span>{t('courier.map.lastSeen', 'Last seen')}: {formatLastSeen(courier.status?.lastSeenAt)}</span>
                    </div>

                    {courier.status?.latitude && courier.status?.longitude && (
                      <div className="flex items-center text-gray-600">
                        <Navigation className="h-4 w-4 mr-2" />
                        <span className="truncate">
                          {courier.status.latitude.toFixed(6)}, {courier.status.longitude.toFixed(6)}
                        </span>
                      </div>
                    )}

                    {courier.phone && (
                      <div className="flex items-center text-gray-600">
                        <span className="font-medium mr-2">📞</span>
                        <span>{courier.phone}</span>
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
