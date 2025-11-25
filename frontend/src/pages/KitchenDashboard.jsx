import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { kitchenAPI } from '../services/api';
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

  useEffect(() => {
    loadRestaurants();
    const interval = setInterval(() => {
      if (selectedRestaurant) {
        loadOrders();
      }
    }, 10000); // Refresh every 10 seconds

    return () => clearInterval(interval);
  }, [selectedRestaurant]);

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
    const now = new Date();
    const minutes = Math.floor((now - start) / 60000);
    return `${minutes} min`;
  };

  if (loading && activeOrders.length === 0 && readyOrders.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  const pendingOrders = activeOrders.filter(o => o.status === 'PENDING');
  const preparingOrders = activeOrders.filter(o => o.status === 'PREPARING');

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
          <Button onClick={loadOrders} variant="outline">
            {t('common.refresh')}
          </Button>
        </div>
      </div>

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
                    <div>
                      <CardTitle className="text-lg">
                        {order.order.orderNumber}
                      </CardTitle>
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
                  <div className="space-y-2">
                    <div className="text-sm">
                      <span className="font-medium">{t('kitchen.fields.estimatedTime')}:</span>
                      <span className="ml-2">{order.estimatedPreparationTimeMinutes} {t('common.minutes')}</span>
                    </div>
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
                    <div>
                      <CardTitle className="text-lg">
                        {order.order.orderNumber}
                      </CardTitle>
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
                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium">{t('kitchen.fields.startedAt')}:</span>
                      <span>{formatTime(order.preparationStartedAt)}</span>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium">{t('kitchen.fields.elapsed')}:</span>
                      <Badge variant="outline" className="font-mono">
                        <Timer className="h-3 w-3 mr-1" />
                        {getElapsedTime(order.preparationStartedAt)}
                      </Badge>
                    </div>
                    <div className="flex items-center justify-between text-sm">
                      <span className="font-medium">{t('kitchen.fields.estimated')}:</span>
                      <span>{order.estimatedPreparationTimeMinutes} {t('common.minutes')}</span>
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
      {readyOrders.length > 0 && (
        <div>
          <h2 className="text-2xl font-bold mb-4 flex items-center gap-2">
            <CheckCircle className="h-6 w-6 text-green-600" />
            {t('kitchen.sections.ready')} ({readyOrders.length})
          </h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {readyOrders.map((order) => (
              <Card key={order.id} className="border-l-4 border-l-green-500">
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <div>
                      <CardTitle className="text-lg">
                        {order.order.orderNumber}
                      </CardTitle>
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
