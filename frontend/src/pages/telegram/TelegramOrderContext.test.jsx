import { describe, expect, it } from 'vitest';
import { buildOrderPayload } from './TelegramOrderContext';

const cart = [
  { product: { id: 1, price: 1000 }, quantity: 2 },
  { product: { id: 5, price: 500 }, quantity: 1 },
];
const prefill = { first_name: 'Ali', last_name: 'V', phone: '+998901234567', latitude: 41.3, longitude: 69.2 };

describe('buildOrderPayload', () => {
  it('tags TELEGRAM_BOT, maps items, and includes delivery info + contact for delivery', () => {
    const p = buildOrderPayload({
      restaurantId: '7',
      prefill,
      cart,
      orderType: 'DELIVERY',
      deliveryInfo: { address: '1 Main St', latitude: 41.3, longitude: 69.2 },
      paymentMethod: 'CASH',
    });
    expect(p.restaurantId).toBe(7); // coerced to Number
    expect(p.orderSource).toBe('TELEGRAM_BOT');
    expect(p.orderType).toBe('DELIVERY');
    expect(p.items).toEqual([{ productId: 1, quantity: 2 }, { productId: 5, quantity: 1 }]);
    expect(p.deliveryInfo).toEqual({ address: '1 Main St', latitude: 41.3, longitude: 69.2 });
    expect(p.customerInfo).toEqual({ firstName: 'Ali', lastName: 'V', phone: '+998901234567' });
    expect(p.paymentMethod).toBe('CASH');
  });

  it('drops delivery info for pickup even if one is passed', () => {
    const p = buildOrderPayload({
      restaurantId: 7,
      prefill,
      cart,
      orderType: 'TAKEAWAY',
      deliveryInfo: { address: 'ignored' },
      paymentMethod: 'CARD',
    });
    expect(p.orderType).toBe('TAKEAWAY');
    expect(p.deliveryInfo).toBeUndefined();
  });

  it('omits customerInfo when there is no prefill', () => {
    const p = buildOrderPayload({
      restaurantId: 7,
      prefill: null,
      cart,
      orderType: 'TAKEAWAY',
      paymentMethod: 'CASH',
    });
    expect(p.customerInfo).toBeUndefined();
    expect(p.customerNotes).toBeUndefined();
  });
});
