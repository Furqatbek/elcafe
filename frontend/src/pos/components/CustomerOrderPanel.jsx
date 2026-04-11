import { useTranslation } from 'react-i18next';

/**
 * CustomerOrderPanel — 30% right panel on the customer-facing display.
 * Shows live order items, modifiers, quantities, prices, and totals.
 * Display-only — no interactive elements.
 */
export default function CustomerOrderPanel({ order }) {
  const { t } = useTranslation();

  const hasItems = order?.items?.length > 0;

  const formatPrice = (n) => {
    if (n == null) return '0';
    return Number(n).toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  };

  return (
    <div className="h-full flex flex-col bg-gray-950 text-white">
      {/* Header */}
      <div className="px-6 py-5 border-b border-gray-800">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-bold">{t('pos.customerDisplay.title', 'Your Order')}</h2>
          {hasItems && (
            <span className="text-lg text-gray-400">
              {order.items.length} {t('pos.customerDisplay.items', 'items')}
            </span>
          )}
        </div>
      </div>

      {/* Items list */}
      <div className="flex-1 overflow-y-auto px-6 py-4">
        {!hasItems ? (
          <div className="h-full flex items-center justify-center">
            <p className="text-xl text-gray-500 text-center">
              {t('pos.customerDisplay.welcomeMessage', 'Your order will appear here')}
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {order.items.map((item, idx) => (
              <div
                key={item.id || idx}
                className="flex justify-between items-start py-3 border-b border-gray-800/50 animate-in slide-in-from-right duration-300"
              >
                <div className="flex-1 min-w-0 pr-4">
                  <div className="flex items-baseline gap-2">
                    <span className="text-lg text-gray-400 font-mono">{item.quantity}×</span>
                    <span className="text-lg font-medium truncate">
                      {item.name}
                      {item.variantName && (
                        <span className="text-gray-400 font-normal"> ({item.variantName})</span>
                      )}
                    </span>
                  </div>
                  {/* Weight info */}
                  {item.weightAmount && (
                    <p className="text-sm text-gray-500 ml-8">
                      {item.weightAmount} {item.weightUnit || 'kg'}
                    </p>
                  )}
                  {/* Modifiers */}
                  {item.modifiers && item.modifiers.length > 0 && (
                    <p className="text-sm text-gray-500 ml-8">
                      {item.modifiers.map(m => m.name).join(', ')}
                    </p>
                  )}
                </div>
                <span className="text-lg font-semibold text-right whitespace-nowrap">
                  {formatPrice(item.itemTotal)}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Totals */}
      {hasItems && (
        <div className="px-6 py-5 border-t border-gray-800 space-y-2">
          <div className="flex justify-between text-gray-400 text-lg">
            <span>{t('pos.customerDisplay.subtotal', 'Subtotal')}</span>
            <span>{formatPrice(order.subtotal)}</span>
          </div>

          {order.serviceFee > 0 && (
            <div className="flex justify-between text-gray-400 text-lg">
              <span>{t('pos.customerDisplay.serviceFee', 'Service Fee')}</span>
              <span>{formatPrice(order.serviceFee)}</span>
            </div>
          )}

          {order.deliveryFee > 0 && (
            <div className="flex justify-between text-gray-400 text-lg">
              <span>{t('pos.customerDisplay.deliveryFee', 'Delivery Fee')}</span>
              <span>{formatPrice(order.deliveryFee)}</span>
            </div>
          )}

          {order.discount > 0 && (
            <div className="flex justify-between text-green-400 text-lg">
              <span>{t('pos.customerDisplay.discount', 'Discount')}</span>
              <span>-{formatPrice(order.discount)}</span>
            </div>
          )}

          <div className="flex justify-between pt-3 border-t border-gray-700">
            <span className="text-2xl font-bold">{t('pos.customerDisplay.total', 'Total')}</span>
            <span className="text-3xl font-bold">{formatPrice(order.total)}</span>
          </div>
        </div>
      )}
    </div>
  );
}
