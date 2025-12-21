import { useEffect, useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { kitchenAPI } from '../services/api';
import audioNotificationService from '../utils/audioNotifications';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  ChefHat,
  Clock,
  CheckCircle,
  AlertCircle,
  Package,
  Flame,
  Timer,
  User,
  ArrowRight,
  TrendingUp,
  Volume2,
  VolumeX,
  Search,
  X,
  Filter,
} from 'lucide-react';

export default function KitchenDashboard() {
  const { t } = useTranslation();
  const [activeOrders, setActiveOrders] = useState([]);
  const [readyOrders, setReadyOrders] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [startModalOpen, setStartModalOpen] = useState(false);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [chefName, setChefName] = useState('');
  const [priorityModalOpen, setPriorityModalOpen] = useState(false);
  const [selectedPriority, setSelectedPriority] = useState('NORMAL');
  const [audioMuted, setAudioMuted] = useState(() => {
    // Load mute state from localStorage
    const saved = localStorage.getItem('kitchenAudioMuted');
    return saved === 'true';
  });
  const stompClientRef = useRef(null);
  const audioInitializedRef = useRef(false);
  const [currentTime, setCurrentTime] = useState(new Date());
  const [searchQuery, setSearchQuery] = useState('');
  const [filterOrderType, setFilterOrderType] = useState('ALL');
  const [filterPriority, setFilterPriority] = useState('ALL');

  // Update current time every second for live timers
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date());
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    loadRestaurants();

    // Initialize audio on first user interaction
    const initAudio = () => {
      if (!audioInitializedRef.current) {
        audioNotificationService.initialize();
        audioNotificationService.setMuted(audioMuted);
        audioInitializedRef.current = true;
        console.log('Audio initialized on user interaction');
      }
    };

    // Listen for any user interaction to initialize audio
    document.addEventListener('click', initAudio, { once: true });
    document.addEventListener('keydown', initAudio, { once: true });

    return () => {
      document.removeEventListener('click', initAudio);
      document.removeEventListener('keydown', initAudio);
    };
  }, [audioMuted]);

  // WebSocket connection effect
  useEffect(() => {
    if (!selectedRestaurant) return;

    // Initial load
    loadOrders();

    // Connect to WebSocket
    connectWebSocket();

    // Fallback polling (in case WebSocket fails)
    const interval = setInterval(() => {
      if (selectedRestaurant) {
        loadOrders();
      }
    }, 30000); // Refresh every 30 seconds as fallback

    return () => {
      clearInterval(interval);
      disconnectWebSocket();
    };
  }, [selectedRestaurant]);

  const connectWebSocket = () => {
    try {
      const socket = new SockJS('/api/ws-waiter');
      const client = new Client({
        webSocketFactory: () => socket,
        debug: (str) => {
          console.log('STOMP: ' + str);
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          console.log('WebSocket connected for kitchen dashboard');

          // Subscribe to restaurant-specific kitchen topic
          client.subscribe(
            `/topic/restaurant/${selectedRestaurant}/kitchen`,
            (message) => {
              try {
                const event = JSON.parse(message.body);
                handleKitchenEvent(event);
              } catch (error) {
                console.error('Error parsing WebSocket message:', error);
              }
            }
          );

          console.log('Subscribed to kitchen updates for restaurant:', selectedRestaurant);
        },
        onStompError: (frame) => {
          console.error('STOMP error:', frame);
        },
        onWebSocketError: (error) => {
          console.error('WebSocket error:', error);
        },
      });

      client.activate();
      stompClientRef.current = client;
    } catch (error) {
      console.error('Failed to connect to WebSocket:', error);
    }
  };

  const disconnectWebSocket = () => {
    if (stompClientRef.current) {
      stompClientRef.current.deactivate();
      stompClientRef.current = null;
      console.log('WebSocket disconnected');
    }
  };

  const handleKitchenEvent = (event) => {
    console.log('Received kitchen event:', event);

    const eventType = event.data?.eventType;
    const eventData = event.data;

    switch (eventType) {
      case 'kitchen.order.created':
        // Play new order sound
        audioNotificationService.playNewOrderSound();
        loadOrders();
        break;
      case 'kitchen.order.preparing':
        loadOrders();
        break;
      case 'kitchen.order.ready':
        // Play order ready sound
        audioNotificationService.playOrderReadySound();
        loadOrders();
        break;
      case 'kitchen.order.picked_up':
        loadOrders();
        break;
      case 'kitchen.order.priority_updated':
        // Play urgent sound if priority is URGENT
        if (eventData?.priority === 'URGENT') {
          audioNotificationService.playUrgentSound();
        } else {
          audioNotificationService.playPriorityChangeSound();
        }
        loadOrders();
        break;
      default:
        console.log('Unknown kitchen event type:', eventType);
    }
  };

  const toggleAudioMute = () => {
    const newMutedState = audioNotificationService.toggleMute();
    setAudioMuted(newMutedState);
    localStorage.setItem('kitchenAudioMuted', newMutedState.toString());

    // Play test sound when unmuting
    if (!newMutedState) {
      setTimeout(() => audioNotificationService.playPriorityChangeSound(), 100);
    }
  };

  const loadRestaurants = async () => {
    try {
      const response = await fetch('/api/v1/restaurants?page=0&size=100');
      const result = await response.json();
      const restaurantList = result.data?.content || [];
      setRestaurants(restaurantList);
      if (restaurantList.length > 0) {
        setSelectedRestaurant(restaurantList[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadOrders = async () => {
    setLoading(true);
    try {
      const [activeRes, readyRes] = await Promise.all([
        kitchenAPI.getActiveOrders(selectedRestaurant),
        kitchenAPI.getReadyOrders(selectedRestaurant),
      ]);

      setActiveOrders(activeRes.data.data || []);
      setReadyOrders(readyRes.data.data || []);
    } catch (error) {
      console.error('Failed to load orders:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleStartPreparation = (order) => {
    setSelectedOrder(order);
    setStartModalOpen(true);
  };

  const confirmStartPreparation = async () => {
    if (!chefName.trim()) {
      alert(t('kitchen.errors.chefNameRequired'));
      return;
    }

    try {
      await kitchenAPI.startPreparation(selectedOrder.id, chefName);
      setStartModalOpen(false);
      setChefName('');
      loadOrders();
    } catch (error) {
      console.error('Failed to start preparation:', error);
      alert(t('kitchen.errors.startFailed'));
    }
  };

  const handleMarkReady = async (orderId) => {
    if (!confirm(t('kitchen.confirmReady'))) return;

    try {
      await kitchenAPI.markReady(orderId);
      loadOrders();
    } catch (error) {
      console.error('Failed to mark as ready:', error);
      alert(t('kitchen.errors.readyFailed'));
    }
  };

  const handleMarkPickedUp = async (orderId) => {
    try {
      await kitchenAPI.markPickedUp(orderId);
      loadOrders();
    } catch (error) {
      console.error('Failed to mark as picked up:', error);
      alert(t('kitchen.errors.pickupFailed'));
    }
  };

  const handleChangePriority = (order) => {
    setSelectedOrder(order);
    setSelectedPriority(order.priority);
    setPriorityModalOpen(true);
  };

  const confirmChangePriority = async () => {
    try {
      await kitchenAPI.updatePriority(selectedOrder.id, selectedPriority);
      setPriorityModalOpen(false);
      loadOrders();
    } catch (error) {
      console.error('Failed to update priority:', error);
      alert(t('kitchen.errors.priorityFailed'));
    }
  };

  const getPriorityColor = (priority) => {
    switch (priority) {
      case 'URGENT': return 'bg-red-100 text-red-800 border-red-300';
      case 'HIGH': return 'bg-orange-100 text-orange-800 border-orange-300';
      case 'NORMAL': return 'bg-blue-100 text-blue-800 border-blue-300';
      case 'LOW': return 'bg-gray-100 text-gray-800 border-gray-300';
      default: return 'bg-gray-100 text-gray-800 border-gray-300';
    }
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'PENDING': return 'bg-yellow-100 text-yellow-800';
      case 'PREPARING': return 'bg-blue-100 text-blue-800';
      case 'READY': return 'bg-green-100 text-green-800';
      case 'PICKED_UP': return 'bg-purple-100 text-purple-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  const formatTime = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  };

  const getElapsedTime = (startTime) => {
    if (!startTime) return '';
    const start = new Date(startTime);
    const now = currentTime;
    const minutes = Math.floor((now - start) / 60000);
    return `${minutes} min`;
  };

  const getElapsedSeconds = (startTime) => {
    if (!startTime) return 0;
    const start = new Date(startTime);
    const now = currentTime;
    return Math.floor((now - start) / 1000);
  };

  const formatElapsedTime = (startTime) => {
    if (!startTime) return '0:00';
    const seconds = getElapsedSeconds(startTime);
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const getTimeColor = (startTime, estimatedMinutes) => {
    if (!startTime || !estimatedMinutes) return 'text-gray-600';

    const elapsedMinutes = getElapsedSeconds(startTime) / 60;
    const percentage = (elapsedMinutes / estimatedMinutes) * 100;

    if (percentage >= 100) return 'text-red-600 font-bold'; // Overdue
    if (percentage >= 80) return 'text-orange-600 font-semibold'; // Warning
    if (percentage >= 60) return 'text-yellow-600'; // Caution
    return 'text-green-600'; // On time
  };

  const getTimerBadgeColor = (startTime, estimatedMinutes) => {
    if (!startTime || !estimatedMinutes) return 'bg-gray-100 text-gray-800';

    const elapsedMinutes = getElapsedSeconds(startTime) / 60;
    const percentage = (elapsedMinutes / estimatedMinutes) * 100;

    if (percentage >= 100) return 'bg-red-100 text-red-800 border-red-300 animate-pulse';
    if (percentage >= 80) return 'bg-orange-100 text-orange-800 border-orange-300';
    if (percentage >= 60) return 'bg-yellow-100 text-yellow-800 border-yellow-300';
    return 'bg-green-100 text-green-800 border-green-300';
  };

  const applyFilters = (orders) => {
    return orders.filter(order => {
      // Search filter (order number)
      if (searchQuery && !order.order.orderNumber.toLowerCase().includes(searchQuery.toLowerCase())) {
        return false;
      }

      // Order type filter
      if (filterOrderType !== 'ALL' && order.order.orderType !== filterOrderType) {
        return false;
      }

      // Priority filter
      if (filterPriority !== 'ALL' && order.priority !== filterPriority) {
        return false;
      }

      return true;
    });
  };

  const clearFilters = () => {
    setSearchQuery('');
    setFilterOrderType('ALL');
    setFilterPriority('ALL');
  };

  const hasActiveFilters = searchQuery || filterOrderType !== 'ALL' || filterPriority !== 'ALL';

  if (loading && activeOrders.length === 0 && readyOrders.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  const pendingOrders = applyFilters(activeOrders.filter(o => o.status === 'PENDING'));
  const preparingOrders = applyFilters(activeOrders.filter(o => o.status === 'PREPARING'));
  const filteredReadyOrders = applyFilters(readyOrders);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('kitchen.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('kitchen.subtitle')}</p>
        </div>
        <div className="flex gap-3 items-center">
          <Select
            value={selectedRestaurant?.toString()}
            onValueChange={(value) => setSelectedRestaurant(parseInt(value))}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('common.selectRestaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map((restaurant) => (
                <SelectItem key={restaurant.id} value={restaurant.id.toString()}>
                  {restaurant.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            onClick={toggleAudioMute}
            variant="outline"
            size="icon"
            title={audioMuted ? 'Unmute audio alerts' : 'Mute audio alerts'}
          >
            {audioMuted ? <VolumeX className="h-4 w-4" /> : <Volume2 className="h-4 w-4" />}
          </Button>
          <Button onClick={loadOrders} variant="outline">
            {t('common.refresh')}
          </Button>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex flex-wrap items-end gap-4">
            <div className="flex-1 min-w-[200px]">
              <Label htmlFor="search" className="mb-2 flex items-center gap-2">
                <Search className="h-4 w-4" />
                Search Order Number
              </Label>
              <Input
                id="search"
                type="text"
                placeholder="Type order number..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full"
              />
            </div>

            <div className="min-w-[180px]">
              <Label htmlFor="orderType" className="mb-2 flex items-center gap-2">
                <Filter className="h-4 w-4" />
                Order Type
              </Label>
              <Select value={filterOrderType} onValueChange={setFilterOrderType}>
                <SelectTrigger id="orderType">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Types</SelectItem>
                  <SelectItem value="DINE_IN">Dine-in</SelectItem>
                  <SelectItem value="DELIVERY">Delivery</SelectItem>
                  <SelectItem value="TAKEAWAY">Takeaway</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="min-w-[180px]">
              <Label htmlFor="priority" className="mb-2 flex items-center gap-2">
                <TrendingUp className="h-4 w-4" />
                Priority
              </Label>
              <Select value={filterPriority} onValueChange={setFilterPriority}>
                <SelectTrigger id="priority">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">All Priorities</SelectItem>
                  <SelectItem value="URGENT">Urgent</SelectItem>
                  <SelectItem value="HIGH">High</SelectItem>
                  <SelectItem value="NORMAL">Normal</SelectItem>
                  <SelectItem value="LOW">Low</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {hasActiveFilters && (
              <Button
                variant="outline"
                onClick={clearFilters}
                className="flex items-center gap-2"
              >
                <X className="h-4 w-4" />
                Clear Filters
              </Button>
            )}
          </div>

          {hasActiveFilters && (
            <div className="mt-4 flex flex-wrap gap-2">
              {searchQuery && (
                <Badge variant="secondary" className="px-3 py-1">
                  Search: "{searchQuery}"
                </Badge>
              )}
              {filterOrderType !== 'ALL' && (
                <Badge variant="secondary" className="px-3 py-1">
                  Type: {filterOrderType === 'DINE_IN' ? 'Dine-in' : filterOrderType}
                </Badge>
              )}
              {filterPriority !== 'ALL' && (
                <Badge variant="secondary" className="px-3 py-1">
                  Priority: {filterPriority}
                </Badge>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Stats Cards */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('kitchen.stats.pending')}</CardTitle>
            <AlertCircle className="h-4 w-4 text-yellow-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{pendingOrders.length}</div>
            <p className="text-xs text-muted-foreground">{t('kitchen.stats.waitingStart')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('kitchen.stats.preparing')}</CardTitle>
            <Flame className="h-4 w-4 text-blue-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{preparingOrders.length}</div>
            <p className="text-xs text-muted-foreground">{t('kitchen.stats.inProgress')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('kitchen.stats.ready')}</CardTitle>
            <CheckCircle className="h-4 w-4 text-green-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{readyOrders.length}</div>
            <p className="text-xs text-muted-foreground">{t('kitchen.stats.awaitingPickup')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t('kitchen.stats.total')}</CardTitle>
            <Package className="h-4 w-4 text-purple-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{activeOrders.length + readyOrders.length}</div>
            <p className="text-xs text-muted-foreground">{t('kitchen.stats.totalActive')}</p>
          </CardContent>
        </Card>
      </div>

      {/* Pending Orders */}
      {pendingOrders.length > 0 && (
        <div>
          <h2 className="text-2xl font-bold mb-4 flex items-center gap-2">
            <AlertCircle className="h-6 w-6 text-yellow-600" />
            {t('kitchen.sections.pending')} ({pendingOrders.length})
          </h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {pendingOrders.map((order) => (
              <Card key={order.id} className="border-l-4 border-l-yellow-500">
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <CardTitle className="text-lg">
                          {order.order.orderNumber}
                        </CardTitle>
                        {order.order.diningTable && (
                          <Badge variant="outline" className="text-xs font-semibold">
                            Table {order.order.diningTable.tableNumber}
                          </Badge>
                        )}
                        {order.order.orderType && (
                          <Badge variant="secondary" className="text-xs">
                            {order.order.orderType === 'DINE_IN' ? 'Dine-in' :
                             order.order.orderType === 'DELIVERY' ? 'Delivery' : 'Takeaway'}
                          </Badge>
                        )}
                      </div>
                      <CardDescription className="flex items-center gap-2 mt-1">
                        <Clock className="h-3 w-3" />
                        {formatTime(order.createdAt)}
                      </CardDescription>
                    </div>
                    <Badge className={getPriorityColor(order.priority)}>
                      {order.priority}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {/* Order Items */}
                  {order.order.items && order.order.items.length > 0 && (
                    <div className="border-2 border-dashed border-gray-200 rounded-lg p-3 bg-gray-50">
                      <div className="space-y-2">
                        {order.order.items.map((item, idx) => (
                          <div key={idx} className="text-sm">
                            <div className="flex items-start justify-between">
                              <div className="flex-1">
                                <span className="font-semibold text-gray-900">
                                  {item.quantity}x {item.productName}
                                </span>
                                {item.variantName && (
                                  <span className="text-gray-600 ml-1">({item.variantName})</span>
                                )}
                              </div>
                            </div>
                            {item.addOns && (
                              <div className="text-xs text-gray-600 ml-4 mt-1">
                                + {item.addOns}
                              </div>
                            )}
                            {item.specialInstructions && (
                              <div className="text-xs text-orange-600 ml-4 mt-1 font-medium">
                                ⚠️ {item.specialInstructions}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Customer Notes */}
                  {order.order.customerNotes && (
                    <div className="border-l-4 border-l-orange-400 bg-orange-50 rounded p-3">
                      <div className="text-xs font-semibold text-orange-800 mb-1">CUSTOMER NOTES:</div>
                      <div className="text-sm text-orange-900">{order.order.customerNotes}</div>
                    </div>
                  )}

                  {/* Live Timer */}
                  <div className="border-2 border-dashed rounded-lg p-3 bg-gradient-to-r from-gray-50 to-white">
                    <div className="flex items-center justify-between">
                      <div className="text-sm font-medium text-gray-700">Waiting Time</div>
                      <Badge className={`font-mono text-lg ${getTimerBadgeColor(order.createdAt, order.estimatedPreparationTimeMinutes)}`}>
                        <Timer className="h-4 w-4 mr-1" />
                        {formatElapsedTime(order.createdAt)}
                      </Badge>
                    </div>
                    <div className="mt-2">
                      <div className="flex justify-between text-xs text-gray-600 mb-1">
                        <span>Target: {order.estimatedPreparationTimeMinutes} min</span>
                        <span className={getTimeColor(order.createdAt, order.estimatedPreparationTimeMinutes)}>
                          {Math.floor(getElapsedSeconds(order.createdAt) / 60)} / {order.estimatedPreparationTimeMinutes} min
                        </span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full transition-all ${
                            getElapsedSeconds(order.createdAt) / 60 >= order.estimatedPreparationTimeMinutes
                              ? 'bg-red-500'
                              : getElapsedSeconds(order.createdAt) / 60 >= order.estimatedPreparationTimeMinutes * 0.8
                              ? 'bg-orange-500'
                              : getElapsedSeconds(order.createdAt) / 60 >= order.estimatedPreparationTimeMinutes * 0.6
                              ? 'bg-yellow-500'
                              : 'bg-green-500'
                          }`}
                          style={{
                            width: `${Math.min(100, (getElapsedSeconds(order.createdAt) / 60 / order.estimatedPreparationTimeMinutes) * 100)}%`
                          }}
                        />
                      </div>
                    </div>
                  </div>

                  <div className="space-y-2">
                    {order.notes && (
                      <div className="text-sm">
                        <span className="font-medium">{t('common.notes')}:</span>
                        <p className="text-muted-foreground mt-1">{order.notes}</p>
                      </div>
                    )}
                  </div>

                  <div className="flex gap-2">
                    <Button
                      className="flex-1"
                      onClick={() => handleStartPreparation(order)}
                    >
                      <ChefHat className="h-4 w-4 mr-2" />
                      {t('kitchen.actions.start')}
                    </Button>
                    <Button
                      variant="outline"
                      size="icon"
                      onClick={() => handleChangePriority(order)}
                    >
                      <TrendingUp className="h-4 w-4" />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Preparing Orders */}
      {preparingOrders.length > 0 && (
        <div>
          <h2 className="text-2xl font-bold mb-4 flex items-center gap-2">
            <Flame className="h-6 w-6 text-blue-600" />
            {t('kitchen.sections.preparing')} ({preparingOrders.length})
          </h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {preparingOrders.map((order) => (
              <Card key={order.id} className="border-l-4 border-l-blue-500">
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <CardTitle className="text-lg">
                          {order.order.orderNumber}
                        </CardTitle>
                        {order.order.diningTable && (
                          <Badge variant="outline" className="text-xs font-semibold">
                            Table {order.order.diningTable.tableNumber}
                          </Badge>
                        )}
                        {order.order.orderType && (
                          <Badge variant="secondary" className="text-xs">
                            {order.order.orderType === 'DINE_IN' ? 'Dine-in' :
                             order.order.orderType === 'DELIVERY' ? 'Delivery' : 'Takeaway'}
                          </Badge>
                        )}
                      </div>
                      <CardDescription className="flex items-center gap-2 mt-1">
                        <User className="h-3 w-3" />
                        {order.assignedChef}
                      </CardDescription>
                    </div>
                    <Badge className={getPriorityColor(order.priority)}>
                      {order.priority}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {/* Order Items */}
                  {order.order.items && order.order.items.length > 0 && (
                    <div className="border-2 border-dashed border-blue-200 rounded-lg p-3 bg-blue-50">
                      <div className="space-y-2">
                        {order.order.items.map((item, idx) => (
                          <div key={idx} className="text-sm">
                            <div className="flex items-start justify-between">
                              <div className="flex-1">
                                <span className="font-semibold text-gray-900">
                                  {item.quantity}x {item.productName}
                                </span>
                                {item.variantName && (
                                  <span className="text-gray-600 ml-1">({item.variantName})</span>
                                )}
                              </div>
                            </div>
                            {item.addOns && (
                              <div className="text-xs text-gray-600 ml-4 mt-1">
                                + {item.addOns}
                              </div>
                            )}
                            {item.specialInstructions && (
                              <div className="text-xs text-orange-600 ml-4 mt-1 font-medium">
                                ⚠️ {item.specialInstructions}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Customer Notes */}
                  {order.order.customerNotes && (
                    <div className="border-l-4 border-l-orange-400 bg-orange-50 rounded p-3">
                      <div className="text-xs font-semibold text-orange-800 mb-1">CUSTOMER NOTES:</div>
                      <div className="text-sm text-orange-900">{order.order.customerNotes}</div>
                    </div>
                  )}

                  {/* Live Cooking Timer */}
                  <div className="border-2 border-dashed border-blue-300 rounded-lg p-3 bg-gradient-to-r from-blue-50 to-white">
                    <div className="flex items-center justify-between">
                      <div className="text-sm font-medium text-blue-900">
                        <Flame className="h-4 w-4 inline mr-1" />
                        Cooking Time
                      </div>
                      <Badge className={`font-mono text-lg ${getTimerBadgeColor(order.preparationStartedAt, order.estimatedPreparationTimeMinutes)}`}>
                        <Timer className="h-4 w-4 mr-1" />
                        {formatElapsedTime(order.preparationStartedAt)}
                      </Badge>
                    </div>
                    <div className="mt-2">
                      <div className="flex justify-between text-xs mb-1">
                        <span className="text-gray-600">Started: {formatTime(order.preparationStartedAt)}</span>
                        <span className={getTimeColor(order.preparationStartedAt, order.estimatedPreparationTimeMinutes)}>
                          {Math.floor(getElapsedSeconds(order.preparationStartedAt) / 60)} / {order.estimatedPreparationTimeMinutes} min
                        </span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full transition-all ${
                            getElapsedSeconds(order.preparationStartedAt) / 60 >= order.estimatedPreparationTimeMinutes
                              ? 'bg-red-500 animate-pulse'
                              : getElapsedSeconds(order.preparationStartedAt) / 60 >= order.estimatedPreparationTimeMinutes * 0.8
                              ? 'bg-orange-500'
                              : getElapsedSeconds(order.preparationStartedAt) / 60 >= order.estimatedPreparationTimeMinutes * 0.6
                              ? 'bg-yellow-500'
                              : 'bg-blue-500'
                          }`}
                          style={{
                            width: `${Math.min(100, (getElapsedSeconds(order.preparationStartedAt) / 60 / order.estimatedPreparationTimeMinutes) * 100)}%`
                          }}
                        />
                      </div>
                    </div>
                  </div>

                  <Button
                    className="w-full"
                    variant="default"
                    onClick={() => handleMarkReady(order.id)}
                  >
                    <CheckCircle className="h-4 w-4 mr-2" />
                    {t('kitchen.actions.markReady')}
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Ready Orders */}
      {filteredReadyOrders.length > 0 && (
        <div>
          <h2 className="text-2xl font-bold mb-4 flex items-center gap-2">
            <CheckCircle className="h-6 w-6 text-green-600" />
            {t('kitchen.sections.ready')} ({filteredReadyOrders.length})
          </h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filteredReadyOrders.map((order) => (
              <Card key={order.id} className="border-l-4 border-l-green-500">
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <CardTitle className="text-lg">
                          {order.order.orderNumber}
                        </CardTitle>
                        {order.order.diningTable && (
                          <Badge variant="outline" className="text-xs font-semibold">
                            Table {order.order.diningTable.tableNumber}
                          </Badge>
                        )}
                        {order.order.orderType && (
                          <Badge variant="secondary" className="text-xs">
                            {order.order.orderType === 'DINE_IN' ? 'Dine-in' :
                             order.order.orderType === 'DELIVERY' ? 'Delivery' : 'Takeaway'}
                          </Badge>
                        )}
                      </div>
                      <CardDescription className="flex items-center gap-2 mt-1">
                        <CheckCircle className="h-3 w-3" />
                        {formatTime(order.preparationCompletedAt)}
                      </CardDescription>
                    </div>
                    <Badge className="bg-green-100 text-green-800">
                      {t('kitchen.status.ready')}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  {/* Order Items */}
                  {order.order.items && order.order.items.length > 0 && (
                    <div className="border-2 border-dashed border-green-200 rounded-lg p-3 bg-green-50">
                      <div className="space-y-2">
                        {order.order.items.map((item, idx) => (
                          <div key={idx} className="text-sm">
                            <div className="flex items-start justify-between">
                              <div className="flex-1">
                                <span className="font-semibold text-gray-900">
                                  {item.quantity}x {item.productName}
                                </span>
                                {item.variantName && (
                                  <span className="text-gray-600 ml-1">({item.variantName})</span>
                                )}
                              </div>
                            </div>
                            {item.addOns && (
                              <div className="text-xs text-gray-600 ml-4 mt-1">
                                + {item.addOns}
                              </div>
                            )}
                            {item.specialInstructions && (
                              <div className="text-xs text-orange-600 ml-4 mt-1 font-medium">
                                ⚠️ {item.specialInstructions}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Customer Notes */}
                  {order.order.customerNotes && (
                    <div className="border-l-4 border-l-orange-400 bg-orange-50 rounded p-3">
                      <div className="text-xs font-semibold text-orange-800 mb-1">CUSTOMER NOTES:</div>
                      <div className="text-sm text-orange-900">{order.order.customerNotes}</div>
                    </div>
                  )}

                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium">{t('kitchen.fields.preparedBy')}:</span>
                      <span>{order.assignedChef}</span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium">{t('kitchen.fields.prepTime')}:</span>
                      <Badge variant="outline" className="font-mono">
                        {order.actualPreparationTimeMinutes} {t('common.minutes')}
                      </Badge>
                    </div>
                  </div>

                  <Button
                    className="w-full"
                    variant="secondary"
                    onClick={() => handleMarkPickedUp(order.id)}
                  >
                    <ArrowRight className="h-4 w-4 mr-2" />
                    {t('kitchen.actions.markPickedUp')}
                  </Button>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Empty State */}
      {activeOrders.length === 0 && readyOrders.length === 0 && !loading && (
        <Card>
          <CardContent className="pt-6">
            <div className="text-center py-12">
              <Package className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
              <p className="text-lg font-medium">{t('kitchen.noOrders')}</p>
              <p className="text-muted-foreground mt-2">{t('kitchen.noOrdersDesc')}</p>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Start Preparation Modal */}
      <Dialog open={startModalOpen} onOpenChange={setStartModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('kitchen.modals.startPreparation')}</DialogTitle>
            <DialogDescription>
              {t('kitchen.modals.startDescription')} {selectedOrder?.order.orderNumber}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="chefName">{t('kitchen.fields.chefName')} *</Label>
              <Input
                id="chefName"
                value={chefName}
                onChange={(e) => setChefName(e.target.value)}
                placeholder={t('kitchen.placeholders.enterChefName')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setStartModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={confirmStartPreparation}>
              {t('kitchen.actions.startPreparation')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Priority Modal */}
      <Dialog open={priorityModalOpen} onOpenChange={setPriorityModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('kitchen.modals.changePriority')}</DialogTitle>
            <DialogDescription>
              {t('kitchen.modals.priorityDescription')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="priority">{t('kitchen.fields.priority')}</Label>
              <Select value={selectedPriority} onValueChange={setSelectedPriority}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="LOW">{t('kitchen.priority.low')}</SelectItem>
                  <SelectItem value="NORMAL">{t('kitchen.priority.normal')}</SelectItem>
                  <SelectItem value="HIGH">{t('kitchen.priority.high')}</SelectItem>
                  <SelectItem value="URGENT">{t('kitchen.priority.urgent')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPriorityModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={confirmChangePriority}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
