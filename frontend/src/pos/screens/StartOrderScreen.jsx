import React from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import { Truck, ShoppingBag, UtensilsCrossed, ClipboardList, Monitor } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';

/**
 * StartOrderScreen - Initial screen for selecting order type
 * Large touch targets for Delivery, Takeaway, Dine-in options
 */
const StartOrderScreen = () => {
  const { t } = useTranslation();
  const { startNewOrder, currentOrder } = usePOSStore();

  const handleOpenCustomerDisplay = () => {
    const restaurantId = localStorage.getItem('selectedRestaurantId') || '1';
    window.open(
      `/admin/pos/customer-display?restaurant=${restaurantId}`,
      'customer-display'
    );
  };

  const orderTypes = [
    {
      type: 'DELIVERY',
      label: t('pos.startOrder.delivery'),
      icon: <Truck className="w-16 h-16" />,
      color: 'blue',
      description: t('pos.startOrder.deliveryDesc'),
    },
    {
      type: 'TAKEAWAY',
      label: t('pos.startOrder.takeaway'),
      icon: <ShoppingBag className="w-16 h-16" />,
      color: 'green',
      description: t('pos.startOrder.takeawayDesc'),
    },
    {
      type: 'DINE_IN',
      label: t('pos.startOrder.dineIn'),
      icon: <UtensilsCrossed className="w-16 h-16" />,
      color: 'purple',
      description: t('pos.startOrder.dineInDesc'),
    },
  ];

  const handleSelectOrderType = (type) => {
    startNewOrder(type);
  };

  const colorClasses = {
    blue: {
      bg: 'bg-blue-50',
      border: 'border-blue-200',
      hover: 'hover:border-blue-400 hover:bg-blue-100',
      active: 'active:bg-blue-200',
      icon: 'text-blue-600',
      ring: 'focus:ring-blue-200',
    },
    green: {
      bg: 'bg-green-50',
      border: 'border-green-200',
      hover: 'hover:border-green-400 hover:bg-green-100',
      active: 'active:bg-green-200',
      icon: 'text-green-600',
      ring: 'focus:ring-green-200',
    },
    purple: {
      bg: 'bg-purple-50',
      border: 'border-purple-200',
      hover: 'hover:border-purple-400 hover:bg-purple-100',
      active: 'active:bg-purple-200',
      icon: 'text-purple-600',
      ring: 'focus:ring-purple-200',
    },
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 p-4 sm:p-6 lg:p-8">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="text-center mb-6 sm:mb-8 lg:mb-12">
          <h1 className="text-2xl sm:text-3xl lg:text-5xl font-bold text-gray-900 mb-2 sm:mb-4">
            {t('pos.startOrder.title')}
          </h1>
          <p className="text-sm sm:text-base lg:text-xl text-gray-600">
            {t('pos.startOrder.subtitle')}
          </p>
        </div>

        {/* Order Type Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-4 lg:gap-6 mb-6 sm:mb-8">
          {orderTypes.map(({ type, label, icon, color, description }) => {
            const colors = colorClasses[color];

            return (
              <button
                key={type}
                onClick={() => handleSelectOrderType(type)}
                className={cn(
                  'flex flex-col items-center justify-center',
                  'min-h-[140px] sm:min-h-[200px] lg:min-h-[320px] p-4 sm:p-6 lg:p-8',
                  'bg-white rounded-xl sm:rounded-2xl',
                  'border-2 sm:border-4 transition-all duration-200',
                  colors.border,
                  colors.hover,
                  colors.active,
                  'active:scale-98',
                  'focus:outline-none focus:ring-4 sm:focus:ring-8',
                  colors.ring
                )}
              >
                {/* Icon */}
                <div className={cn('mb-2 sm:mb-4 lg:mb-6', colors.icon)}>
                  {React.cloneElement(icon, { className: 'w-10 h-10 sm:w-12 sm:h-12 lg:w-16 lg:h-16' })}
                </div>

                {/* Label */}
                <h2 className="text-lg sm:text-xl lg:text-3xl font-bold text-gray-900 mb-1 sm:mb-2 lg:mb-3">
                  {label}
                </h2>

                {/* Description */}
                <p className="text-xs sm:text-sm lg:text-base text-gray-600 text-center hidden sm:block">
                  {description}
                </p>
              </button>
            );
          })}
        </div>

        {/* Active Orders Quick Access */}
        <div className="bg-white rounded-lg sm:rounded-xl p-4 sm:p-6 border-2 border-gray-200">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4">
            <h3 className="text-base sm:text-xl font-semibold text-gray-900 flex items-center gap-2">
              <ClipboardList className="w-5 h-5 sm:w-6 sm:h-6 text-gray-500" />
              {t('pos.orders.activeOrders', 'Active Orders')}
            </h3>
            <div className="flex gap-2 w-full sm:w-auto">
              <TouchButton
                variant="secondary"
                size="medium"
                onClick={() => usePOSStore.getState().setCurrentScreen('active-orders')}
                className="flex-1 sm:flex-initial"
              >
                {t('pos.orders.viewActive', 'View Active Orders')}
              </TouchButton>
              <TouchButton
                variant="secondary"
                size="medium"
                onClick={handleOpenCustomerDisplay}
                icon={<Monitor className="w-5 h-5" />}
                className="flex-1 sm:flex-initial"
              >
                {t('pos.customerDisplay.openDisplay', 'Customer Display')}
              </TouchButton>
            </div>
          </div>
          <p className="text-xs sm:text-sm lg:text-base text-gray-600 mt-2">
            {t('pos.orders.activeDesc', 'Modify orders, split bills, or process payments for dine-in tables')}
          </p>
        </div>

        {/* Active Order Notice */}
        {currentOrder.items.length > 0 && (
          <div className="mt-4 sm:mt-6 bg-amber-50 border-2 border-amber-200 rounded-lg sm:rounded-xl p-4 sm:p-6">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3 sm:gap-4">
              <div>
                <h3 className="text-base sm:text-lg font-semibold text-amber-900 mb-1">
                  {t('common.activeOrderInProgress', 'Active Order in Progress')}
                </h3>
                <p className="text-sm sm:text-base text-amber-700">
                  {t('common.incompleteOrderMessage', 'You have an incomplete {{type}} order with {{count}} item(s)', {
                    type: currentOrder.type?.toLowerCase(),
                    count: currentOrder.items.length
                  })}
                </p>
              </div>
              <TouchButton
                variant="warning"
                size="medium"
                onClick={() => usePOSStore.getState().setCurrentScreen('cart')}
                className="w-full sm:w-auto"
              >
                {t('pos.cart.title')}
              </TouchButton>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StartOrderScreen;
