import React from 'react';
import usePOSStore from '../store/posStore';
import { formatDateTime } from '../../utils/dateUtils';

/**
 * KitchenTicket - Print-optimized kitchen ticket
 * Thermal printer friendly layout, clear item listing
 */
const KitchenTicket = () => {
  const { currentOrder, customer } = usePOSStore();

  const orderDateTime = formatDateTime(new Date());

  return (
    <div className="kitchen-ticket max-w-[80mm] mx-auto p-4 font-mono text-sm bg-white">
      {/* Restaurant Header */}
      <div className="text-center mb-4 border-b-2 border-dashed border-gray-800 pb-4">
        <h1 className="text-2xl font-bold">KITCHEN ORDER</h1>
        <p className="text-xs mt-2">{orderDateTime}</p>
      </div>

      {/* Order Info */}
      <div className="mb-4 space-y-1">
        <div className="flex justify-between font-bold text-lg">
          <span>ORDER #:</span>
          <span>{currentOrder.orderNumber || 'PENDING'}</span>
        </div>
        <div className="flex justify-between">
          <span>Type:</span>
          <span className="font-bold">{currentOrder.type}</span>
        </div>
        {customer.name && (
          <div className="flex justify-between">
            <span>Customer:</span>
            <span>{customer.name}</span>
          </div>
        )}
        {customer.tableNumber && (
          <div className="flex justify-between font-bold text-lg">
            <span>TABLE:</span>
            <span>{customer.tableNumber}</span>
          </div>
        )}
        {customer.guestCount && (
          <div className="flex justify-between">
            <span>Guests:</span>
            <span>{customer.guestCount}</span>
          </div>
        )}
      </div>

      {/* Items */}
      <div className="border-t-2 border-b-2 border-gray-800 py-3 mb-4">
        <h2 className="font-bold text-base mb-3">ITEMS:</h2>
        <div className="space-y-4">
          {currentOrder.items?.map((item, index) => (
            <div key={item.id} className="border-b border-dashed border-gray-400 pb-3 last:border-0">
              {/* Quantity and Name */}
              <div className="flex gap-2 font-bold text-base mb-1">
                <span className="w-8 text-center bg-gray-800 text-white rounded">
                  {item.quantity}x
                </span>
                <span className="flex-1">{item.name}</span>
              </div>

              {/* Modifiers */}
              {item.modifiers && item.modifiers.length > 0 && (
                <div className="ml-10 space-y-1">
                  {item.modifiers.map((mod, modIndex) => (
                    <div key={modIndex} className="text-sm">
                      + {mod.name}
                    </div>
                  ))}
                </div>
              )}

              {/* Item Notes */}
              {item.notes && (
                <div className="ml-10 mt-2 p-2 bg-gray-100 rounded">
                  <p className="text-xs font-bold">NOTE:</p>
                  <p className="text-sm">{item.notes}</p>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Order Notes */}
      {currentOrder.notes && (
        <div className="mb-4 p-3 bg-yellow-100 border-2 border-yellow-400 rounded">
          <p className="font-bold text-sm mb-1">⚠ ORDER NOTES:</p>
          <p className="text-sm whitespace-pre-wrap">{currentOrder.notes}</p>
        </div>
      )}

      {/* Delivery Info */}
      {currentOrder.type === 'DELIVERY' && customer.address && (
        <div className="mb-4 p-2 border-2 border-gray-800">
          <p className="font-bold text-sm mb-1">DELIVERY TO:</p>
          <p className="text-xs">
            {customer.address.street}
            <br />
            {customer.address.city}, {customer.address.zipCode}
          </p>
          {customer.deliveryInstructions && (
            <p className="text-xs mt-1 italic">
              Note: {customer.deliveryInstructions}
            </p>
          )}
        </div>
      )}

      {/* Footer */}
      <div className="text-center border-t-2 border-dashed border-gray-800 pt-4 mt-4">
        <p className="text-xs">
          Items: {currentOrder.items?.reduce((sum, item) => sum + item.quantity, 0) || 0}
        </p>
        <p className="text-xs font-bold mt-2">
          ═══════════════════════
        </p>
      </div>

      {/* Print Styles */}
      <style>{`
        @media print {
          .kitchen-ticket {
            max-width: 80mm;
            margin: 0;
            padding: 8mm;
            font-size: 12pt;
          }

          body {
            margin: 0;
            padding: 0;
          }

          @page {
            size: 80mm auto;
            margin: 0;
          }
        }
      `}</style>
    </div>
  );
};

export default KitchenTicket;
