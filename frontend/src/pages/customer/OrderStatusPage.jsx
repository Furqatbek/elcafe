import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { selfServiceAPI } from '../../services/api';
import {
  Clock,
  ChefHat,
  CheckCircle,
  Package,
  AlertCircle,
  RefreshCw,
  Home,
} from 'lucide-react';

const STATUS_CONFIG = {
  PENDING: {
    icon: Clock,
    labelKey: 'selfService.tracking.statuses.PENDING.label',
    descriptionKey: 'selfService.tracking.statuses.PENDING.description',
    color: 'text-orange-500',
    bgColor: 'bg-orange-50',
    step: 1,
  },
  CONFIRMED: {
    icon: CheckCircle,
    labelKey: 'selfService.tracking.statuses.CONFIRMED.label',
    descriptionKey: 'selfService.tracking.statuses.CONFIRMED.description',
    color: 'text-blue-500',
    bgColor: 'bg-blue-50',
    step: 2,
  },
  PREPARING: {
    icon: ChefHat,
    labelKey: 'selfService.tracking.statuses.PREPARING.label',
    descriptionKey: 'selfService.tracking.statuses.PREPARING.description',
    color: 'text-purple-500',
    bgColor: 'bg-purple-50',
    step: 3,
  },
  READY: {
    icon: Package,
    labelKey: 'selfService.tracking.statuses.READY.label',
    descriptionKey: 'selfService.tracking.statuses.READY.description',
    color: 'text-green-500',
    bgColor: 'bg-green-50',
    step: 4,
  },
  COMPLETED: {
    icon: CheckCircle,
    labelKey: 'selfService.tracking.statuses.COMPLETED.label',
    descriptionKey: 'selfService.tracking.statuses.COMPLETED.description',
    color: 'text-green-600',
    bgColor: 'bg-green-50',
    step: 5,
  },
  CANCELLED: {
    icon: AlertCircle,
    labelKey: 'selfService.tracking.statuses.CANCELLED.label',
    descriptionKey: 'selfService.tracking.statuses.CANCELLED.description',
    color: 'text-red-500',
    bgColor: 'bg-red-50',
    step: 0,
  },
};

export default function OrderStatusPage() {
  const { t } = useTranslation();
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    loadOrderStatus();
    // Poll for updates every 10 seconds
    const interval = setInterval(loadOrderStatus, 10000);
    return () => clearInterval(interval);
  }, [orderId]);

  const loadOrderStatus = async () => {
    try {
      const response = await selfServiceAPI.getOrderStatus(orderId);
      setOrder(response.data);
      setError(null);
    } catch (err) {
      setError(t('selfService.tracking.loadError'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleRefresh = () => {
    setRefreshing(true);
    loadOrderStatus();
  };

  const formatPrice = (price) => {
    return new Intl.NumberFormat('uz-UZ').format(price) + ' UZS';
  };

  const formatTime = (dateString) => {
    if (!dateString) return null;
    const date = new Date(dateString);
    return date.toLocaleTimeString('uz-UZ', { hour: '2-digit', minute: '2-digit' });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error || !order) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
          <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-500" />
          <h2 className="text-xl font-semibold text-gray-800 mb-2">{t('selfService.tracking.error')}</h2>
          <p className="text-gray-600 mb-4">{error || t('selfService.tracking.orderNotFoundShort')}</p>
          <button
            onClick={handleRefresh}
            className="bg-blue-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-blue-700"
          >
            {t('selfService.tracking.tryAgain')}
          </button>
        </div>
      </div>
    );
  }

  const statusConfig = STATUS_CONFIG[order.status] || STATUS_CONFIG.PENDING;
  const StatusIcon = statusConfig.icon;
  const steps = ['PENDING', 'CONFIRMED', 'PREPARING', 'READY'];
  const currentStepIndex = steps.indexOf(order.status);

  return (
    <div className="min-h-screen bg-gray-50 pb-8">
      {/* Header */}
      <div className="bg-white shadow-sm">
        <div className="max-w-lg mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-lg font-bold text-gray-900">{t('selfService.tracking.orderLabel', { number: order.orderNumber })}</h1>
              <p className="text-sm text-gray-500">{order.orderType === 'TAKEAWAY' ? t('selfService.takeaway') : t('selfService.dineIn')}</p>
            </div>
            <button
              onClick={handleRefresh}
              disabled={refreshing}
              className="p-2 text-gray-600 hover:bg-gray-100 rounded-full"
            >
              <RefreshCw className={`w-5 h-5 ${refreshing ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-6 space-y-6">
        {/* Status Card */}
        <div className={`rounded-xl p-6 ${statusConfig.bgColor}`}>
          <div className="flex items-center gap-4">
            <div className={`p-3 rounded-full bg-white ${statusConfig.color}`}>
              <StatusIcon className="w-8 h-8" />
            </div>
            <div>
              <h2 className={`text-xl font-bold ${statusConfig.color}`}>
                {t(statusConfig.labelKey)}
              </h2>
              <p className="text-gray-600 mt-1">{t(statusConfig.descriptionKey)}</p>
            </div>
          </div>

          {/* Estimated Time */}
          {order.estimatedReadyTime && order.status !== 'COMPLETED' && order.status !== 'CANCELLED' && (
            <div className="mt-4 pt-4 border-t border-gray-200">
              <div className="flex items-center gap-2 text-gray-700">
                <Clock className="w-5 h-5" />
                <span>{t('selfService.tracking.estimatedReadyLabel')}: <strong>{formatTime(order.estimatedReadyTime)}</strong></span>
              </div>
            </div>
          )}

          {/* Actual Ready Time */}
          {order.actualReadyTime && (
            <div className="mt-2">
              <div className="flex items-center gap-2 text-green-600">
                <CheckCircle className="w-5 h-5" />
                <span>{t('selfService.tracking.readyAtLabel')}: <strong>{formatTime(order.actualReadyTime)}</strong></span>
              </div>
            </div>
          )}
        </div>

        {/* Progress Steps */}
        {order.status !== 'CANCELLED' && (
          <div className="bg-white rounded-xl p-6 shadow-sm">
            <h3 className="font-semibold text-gray-900 mb-4">{t('selfService.tracking.orderProgress')}</h3>
            <div className="relative">
              {/* Progress Line */}
              <div className="absolute left-4 top-4 bottom-4 w-0.5 bg-gray-200">
                <div
                  className="bg-blue-600 w-full transition-all duration-500"
                  style={{ height: `${(currentStepIndex / (steps.length - 1)) * 100}%` }}
                />
              </div>

              {/* Steps */}
              <div className="space-y-6">
                {steps.map((step, index) => {
                  const stepConfig = STATUS_CONFIG[step];
                  const isCompleted = index <= currentStepIndex;
                  const isCurrent = index === currentStepIndex;
                  const StepIcon = stepConfig.icon;

                  return (
                    <div key={step} className="flex items-center gap-4 relative">
                      <div
                        className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 z-10 ${
                          isCompleted
                            ? 'bg-blue-600 text-white'
                            : 'bg-gray-200 text-gray-400'
                        } ${isCurrent ? 'ring-4 ring-blue-200' : ''}`}
                      >
                        {isCompleted ? (
                          <CheckCircle className="w-5 h-5" />
                        ) : (
                          <StepIcon className="w-4 h-4" />
                        )}
                      </div>
                      <div>
                        <p className={`font-medium ${isCompleted ? 'text-gray-900' : 'text-gray-400'}`}>
                          {t(stepConfig.labelKey)}
                        </p>
                        {isCurrent && (
                          <p className="text-sm text-blue-600">{t(stepConfig.descriptionKey)}</p>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* Order Total */}
        <div className="bg-white rounded-xl p-6 shadow-sm">
          <div className="flex justify-between items-center">
            <span className="text-gray-600">{t('selfService.tracking.totalAmount')}</span>
            <span className="text-xl font-bold text-blue-600">{formatPrice(order.total)}</span>
          </div>
        </div>

        {/* Order Another */}
        {(order.status === 'COMPLETED' || order.status === 'READY') && (
          <button
            onClick={() => navigate('/')}
            className="w-full bg-blue-600 text-white rounded-xl py-4 font-semibold hover:bg-blue-700 transition-colors flex items-center justify-center gap-2"
          >
            <Home className="w-5 h-5" />
            {t('selfService.tracking.orderAgain')}
          </button>
        )}

        {/* Call Waiter (for READY status) */}
        {order.status === 'READY' && order.orderType === 'DINE_IN' && (
          <div className="bg-green-50 rounded-xl p-4 text-center">
            <p className="text-green-700 font-medium">
              {t('selfService.tracking.readyDineInMessage')}
            </p>
          </div>
        )}

        {order.status === 'READY' && order.orderType === 'TAKEAWAY' && (
          <div className="bg-green-50 rounded-xl p-4 text-center">
            <p className="text-green-700 font-medium">
              {t('selfService.tracking.readyTakeawayMessage')}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
