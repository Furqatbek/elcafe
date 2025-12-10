import React from 'react';
import { cn } from '../../lib/utils';
import { Truck, ShoppingBag, UtensilsCrossed, Clock } from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';

/**
 * StartOrderScreen - Initial screen for selecting order type
 * Large touch targets for Delivery, Takeaway, Dine-in options
 */
const StartOrderScreen = () => {
  const { startNewOrder, currentOrder } = usePOSStore();

  const orderTypes = [
    {
      type: 'DELIVERY',
      label: 'Delivery',
      icon: <Truck className="w-16 h-16" />,
      color: 'blue',
      description: 'Order will be delivered to customer address',
    },
    {
      type: 'TAKEAWAY',
      label: 'Takeaway',
      icon: <ShoppingBag className="w-16 h-16" />,
      color: 'green',
      description: 'Customer will pick up the order',
    },
    {
      type: 'DINE_IN',
      label: 'Dine In',
      icon: <UtensilsCrossed className="w-16 h-16" />,
      color: 'purple',
      description: 'Customer will eat at the restaurant',
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
    <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 p-8">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="text-center mb-12">
          <h1 className="text-5xl font-bold text-gray-900 mb-4">
            Start New Order
          </h1>
          <p className="text-xl text-gray-600">
            Select order type to begin
          </p>
        </div>

        {/* Order Type Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          {orderTypes.map(({ type, label, icon, color, description }) => {
            const colors = colorClasses[color];

            return (
              <button
                key={type}
                onClick={() => handleSelectOrderType(type)}
                className={cn(
                  'flex flex-col items-center justify-center',
                  'min-h-[320px] p-8',
                  'bg-white rounded-2xl',
                  'border-4 transition-all duration-200',
                  colors.border,
                  colors.hover,
                  colors.active,
                  'active:scale-98',
                  'focus:outline-none focus:ring-8',
                  colors.ring
                )}
              >
                {/* Icon */}
                <div className={cn('mb-6', colors.icon)}>
                  {icon}
                </div>

                {/* Label */}
                <h2 className="text-3xl font-bold text-gray-900 mb-3">
                  {label}
                </h2>

                {/* Description */}
                <p className="text-base text-gray-600 text-center">
                  {description}
                </p>
              </button>
            );
          })}
        </div>

        {/* Recent Orders Quick Access */}
        <div className="bg-white rounded-xl p-6 border-2 border-gray-200">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-xl font-semibold text-gray-900 flex items-center gap-2">
              <Clock className="w-6 h-6 text-gray-500" />
              Recent Orders
            </h3>
          </div>
          <div className="text-center py-8 text-gray-500">
            No recent orders to display
          </div>
        </div>

        {/* Active Order Notice */}
        {currentOrder.items.length > 0 && (
          <div className="mt-6 bg-amber-50 border-2 border-amber-200 rounded-xl p-6">
            <div className="flex items-start justify-between">
              <div>
                <h3 className="text-lg font-semibold text-amber-900 mb-1">
                  Active Order in Progress
                </h3>
                <p className="text-amber-700">
                  You have an incomplete {currentOrder.type?.toLowerCase()} order with {currentOrder.items.length} item(s)
                </p>
              </div>
              <TouchButton
                variant="warning"
                size="medium"
                onClick={() => usePOSStore.getState().setCurrentScreen('cart')}
              >
                View Cart
              </TouchButton>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StartOrderScreen;
