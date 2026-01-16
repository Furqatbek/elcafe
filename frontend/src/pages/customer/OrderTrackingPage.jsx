import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { orderTrackingAPI } from '../../services/api';
import {
  Clock,
  CheckCircle,
  ChefHat,
  Package,
  Truck,
  MapPin,
  Phone,
  RefreshCw,
  AlertCircle,
} from 'lucide-react';

const statusSteps = [
  { key: 'NEW', icon: Clock, label: 'Order Placed' },
  { key: 'CONFIRMED', icon: CheckCircle, label: 'Confirmed' },
  { key: 'PREPARING', icon: ChefHat, label: 'Preparing' },
  { key: 'READY', icon: Package, label: 'Ready' },
  { key: 'OUT_FOR_DELIVERY', icon: Truck, label: 'On the Way' },
  { key: 'DELIVERED', icon: MapPin, label: 'Delivered' },
];

const statusStepsPickup = [
  { key: 'NEW', icon: Clock, label: 'Order Placed' },
  { key: 'CONFIRMED', icon: CheckCircle, label: 'Confirmed' },
  { key: 'PREPARING', icon: ChefHat, label: 'Preparing' },
  { key: 'READY', icon: Package, label: 'Ready for Pickup' },
  { key: 'COMPLETED', icon: CheckCircle, label: 'Picked Up' },
];

export default function OrderTrackingPage() {
  const { orderNumber } = useParams();
  const [searchParams] = useSearchParams();
  const [tracking, setTracking] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);

  // Auto-refresh every 30 seconds for active orders
  useEffect(() => {
    let interval;
    if (tracking && !['COMPLETED', 'DELIVERED', 'CANCELLED'].includes(tracking.status)) {
      interval = setInterval(loadTracking, 30000);
    }
    return () => clearInterval(interval);
  }, [tracking?.status]);

  useEffect(() => {
    if (orderNumber) {
      loadTracking();
    }
  }, [orderNumber]);

  const loadTracking = async () => {
    try {
      setError(null);
      const response = await orderTrackingAPI.getStatus(orderNumber);
      setTracking(response.data.data);
      setLastUpdated(new Date());
    } catch (err) {
      setError('Order not found or tracking unavailable');
      console.error('Failed to load tracking:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatTime = (dateStr) => {
    if (!dateStr) return null;
    const date = new Date(dateStr);
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return null;
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  };

  const getSteps = () => {
    if (!tracking) return statusSteps;
    return tracking.orderType === 'DELIVERY' ? statusSteps : statusStepsPickup;
  };

  const getCurrentStepIndex = () => {
    if (!tracking) return 0;
    const steps = getSteps();
    const index = steps.findIndex(s => s.key === tracking.status);
    return index >= 0 ? index : 0;
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-600">Loading order status...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="text-center max-w-md">
          <AlertCircle className="h-16 w-16 text-red-400 mx-auto mb-4" />
          <h2 className="text-xl font-bold text-gray-900 mb-2">Order Not Found</h2>
          <p className="text-gray-600 mb-4">{error}</p>
          <p className="text-sm text-gray-500">
            Please check your order number and try again.
          </p>
        </div>
      </div>
    );
  }

  const steps = getSteps();
  const currentStepIndex = getCurrentStepIndex();

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white shadow-sm">
        <div className="max-w-lg mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-lg font-bold text-gray-900">Order #{orderNumber}</h1>
              <p className="text-sm text-gray-500">{tracking?.restaurantName}</p>
            </div>
            <button
              onClick={loadTracking}
              className="p-2 text-gray-500 hover:text-blue-600 transition-colors"
              title="Refresh"
            >
              <RefreshCw className="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-6 space-y-6">
        {/* Status Banner */}
        {tracking?.status === 'CANCELLED' ? (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-center">
            <AlertCircle className="h-8 w-8 text-red-500 mx-auto mb-2" />
            <h2 className="font-semibold text-red-800">Order Cancelled</h2>
            <p className="text-sm text-red-600">This order has been cancelled.</p>
          </div>
        ) : (
          <>
            {/* ETA Card */}
            {tracking?.eta && (
              <div className="bg-white rounded-lg shadow-sm p-4">
                <div className="text-center">
                  <div className="text-sm text-gray-500 mb-1">
                    {tracking.orderType === 'DELIVERY' ? 'Estimated Delivery' : 'Estimated Ready'}
                  </div>
                  <div className="text-3xl font-bold text-blue-600">
                    {tracking.eta.etaMessage}
                  </div>
                  {tracking.eta.isDelayed && (
                    <div className="mt-2 text-sm text-orange-600 flex items-center justify-center gap-1">
                      <AlertCircle className="h-4 w-4" />
                      Slight delay - {tracking.eta.delayReason}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Progress Steps */}
            <div className="bg-white rounded-lg shadow-sm p-4">
              <h3 className="font-semibold text-gray-900 mb-4">Order Progress</h3>
              <div className="relative">
                {/* Progress Line */}
                <div className="absolute left-5 top-0 bottom-0 w-0.5 bg-gray-200" />
                <div
                  className="absolute left-5 top-0 w-0.5 bg-blue-600 transition-all duration-500"
                  style={{ height: `${(currentStepIndex / (steps.length - 1)) * 100}%` }}
                />

                {/* Steps */}
                <div className="space-y-6">
                  {steps.map((step, index) => {
                    const isActive = index <= currentStepIndex;
                    const isCurrent = index === currentStepIndex;
                    const Icon = step.icon;

                    return (
                      <div key={step.key} className="relative flex items-start">
                        <div
                          className={`relative z-10 flex items-center justify-center w-10 h-10 rounded-full border-2 transition-colors ${
                            isActive
                              ? 'bg-blue-600 border-blue-600 text-white'
                              : 'bg-white border-gray-300 text-gray-400'
                          } ${isCurrent ? 'ring-4 ring-blue-100' : ''}`}
                        >
                          <Icon className="h-5 w-5" />
                        </div>
                        <div className="ml-4 flex-1">
                          <div className={`font-medium ${isActive ? 'text-gray-900' : 'text-gray-400'}`}>
                            {step.label}
                          </div>
                          {tracking?.statusHistory?.find(h => h.status === step.key) && (
                            <div className="text-xs text-gray-500">
                              {formatTime(tracking.statusHistory.find(h => h.status === step.key)?.timestamp)}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </>
        )}

        {/* Order Items */}
        {tracking?.items && tracking.items.length > 0 && (
          <div className="bg-white rounded-lg shadow-sm p-4">
            <h3 className="font-semibold text-gray-900 mb-3">Order Items</h3>
            <div className="space-y-2">
              {tracking.items.map((item, index) => (
                <div key={index} className="flex justify-between text-sm">
                  <div>
                    <span className="font-medium">{item.quantity}x</span>{' '}
                    {item.name}
                    {item.notes && (
                      <div className="text-xs text-gray-500">{item.notes}</div>
                    )}
                  </div>
                  <div className="text-gray-600">
                    {item.price?.toFixed(2)}
                  </div>
                </div>
              ))}
              <div className="border-t pt-2 mt-2 flex justify-between font-semibold">
                <span>Total</span>
                <span>{tracking.totalAmount?.toFixed(2)}</span>
              </div>
            </div>
          </div>
        )}

        {/* Delivery Info */}
        {tracking?.deliveryInfo && tracking.orderType === 'DELIVERY' && (
          <div className="bg-white rounded-lg shadow-sm p-4">
            <h3 className="font-semibold text-gray-900 mb-3">Delivery Details</h3>
            <div className="space-y-3">
              <div className="flex items-start gap-3">
                <MapPin className="h-5 w-5 text-gray-400 mt-0.5" />
                <div>
                  <div className="text-sm font-medium">Delivery Address</div>
                  <div className="text-sm text-gray-600">{tracking.deliveryInfo.deliveryAddress}</div>
                </div>
              </div>
              {tracking.deliveryInfo.courierName && (
                <div className="flex items-center gap-3">
                  <Truck className="h-5 w-5 text-gray-400" />
                  <div>
                    <div className="text-sm font-medium">{tracking.deliveryInfo.courierName}</div>
                    {tracking.deliveryInfo.courierPhone && (
                      <a
                        href={`tel:${tracking.deliveryInfo.courierPhone}`}
                        className="text-sm text-blue-600 flex items-center gap-1"
                      >
                        <Phone className="h-3 w-3" />
                        {tracking.deliveryInfo.courierPhone}
                      </a>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Restaurant Info */}
        <div className="bg-white rounded-lg shadow-sm p-4">
          <h3 className="font-semibold text-gray-900 mb-3">Restaurant</h3>
          <div className="space-y-2">
            <div className="font-medium">{tracking?.restaurantName}</div>
            {tracking?.restaurantAddress && (
              <div className="text-sm text-gray-600 flex items-start gap-2">
                <MapPin className="h-4 w-4 text-gray-400 mt-0.5" />
                {tracking.restaurantAddress}
              </div>
            )}
            {tracking?.restaurantPhone && (
              <a
                href={`tel:${tracking.restaurantPhone}`}
                className="text-sm text-blue-600 flex items-center gap-2"
              >
                <Phone className="h-4 w-4" />
                {tracking.restaurantPhone}
              </a>
            )}
          </div>
        </div>

        {/* Last Updated */}
        {lastUpdated && (
          <div className="text-center text-xs text-gray-400">
            Last updated: {lastUpdated.toLocaleTimeString()}
          </div>
        )}
      </div>
    </div>
  );
}
