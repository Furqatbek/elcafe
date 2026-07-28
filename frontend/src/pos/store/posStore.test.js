import { describe, it, expect, beforeEach, vi } from 'vitest';

// The store imports the axios-backed API layer at module load. None of the cart-math actions under
// test touch it, but mocking keeps the import hermetic (no real axios/network wiring in jsdom).
vi.mock('../../services/api', () => ({
  posAPI: {},
  tablesAPI: {},
  bundleAPI: {},
  promotionAPI: {},
}));

import usePOSStore from './posStore';
import { posAPI } from '../../services/api';

/**
 * Unit tests for the POS cart math — the money-handling core of the till. These actions are pure and
 * synchronous (no API), which is exactly why they are worth pinning: a subtotal/service-fee/total
 * regression here is a wrong number on a customer's receipt.
 *
 * The invariant every mutation must preserve:
 *   serviceFee = subtotal * serviceFeePercent / 100
 *   total      = max(0, subtotal + tax + deliveryFee + serviceFee + entryFee - discount)   (tax is always 0)
 */

// A clean order with every fee/discount zeroed, so each test controls exactly the inputs it exercises.
const freshOrder = (overrides = {}) => ({
  id: null,
  orderNumber: null,
  type: 'TAKEAWAY',
  items: [],
  subtotal: 0,
  tax: 0,
  deliveryFee: 0,
  serviceFeePercent: 0,
  serviceFee: 0,
  entryFee: 0,
  discount: 0,
  discountType: null,
  couponCode: null,
  promotionId: null,
  promotionName: null,
  discountReason: null,
  total: 0,
  notes: '',
  ...overrides,
});

const order = () => usePOSStore.getState().currentOrder;
const payment = () => usePOSStore.getState().payment;
const splitBill = () => usePOSStore.getState().splitBill;
const resetOrder = (overrides = {}) => usePOSStore.setState({ currentOrder: freshOrder(overrides) });

const coffee = { id: 1, name: 'Coffee', price: 5 };
const pizza = { id: 2, name: 'Pizza', price: 10 };

beforeEach(() => {
  localStorage.clear();
  usePOSStore.setState({
    currentOrder: freshOrder(),
    payment: { method: null, amountTendered: 0, changeDue: 0, status: null },
    splitBill: { active: false, result: null, currentSplitIndex: null, originalOrderTotal: 0, paidSplits: [] },
  });
});

describe('startNewOrder', () => {
  it('resets the cart and routes DINE_IN to table selection', () => {
    resetOrder({ items: [{ id: 'x', itemTotal: 9 }], subtotal: 9, total: 9 });
    usePOSStore.getState().startNewOrder('DINE_IN');

    expect(order().type).toBe('DINE_IN');
    expect(order().items).toEqual([]);
    expect(order().subtotal).toBe(0);
    expect(order().total).toBe(0);
    expect(order().deliveryFee).toBe(0);
    expect(usePOSStore.getState().ui.currentScreen).toBe('tables');
    expect(usePOSStore.getState().selectedTables).toEqual([]);
  });

  it('seeds a 5.00 delivery fee for DELIVERY and routes to the menu', () => {
    usePOSStore.getState().startNewOrder('DELIVERY');
    expect(order().type).toBe('DELIVERY');
    expect(order().deliveryFee).toBe(5.0);
    expect(usePOSStore.getState().ui.currentScreen).toBe('menu');
  });

  it('routes TAKEAWAY to the menu with no delivery fee', () => {
    usePOSStore.getState().startNewOrder('TAKEAWAY');
    expect(order().deliveryFee).toBe(0);
    expect(usePOSStore.getState().ui.currentScreen).toBe('menu');
  });
});

describe('addItemToCart', () => {
  it('adds a new line and computes subtotal/total', () => {
    usePOSStore.getState().addItemToCart(coffee);
    expect(order().items).toHaveLength(1);
    expect(order().items[0].itemTotal).toBe(5);
    expect(order().subtotal).toBe(5);
    expect(order().total).toBe(5);
  });

  it('folds an identical product+modifier line into a quantity bump, not a second line', () => {
    usePOSStore.getState().addItemToCart(coffee);
    usePOSStore.getState().addItemToCart(coffee, [], 2);
    expect(order().items).toHaveLength(1);
    expect(order().items[0].quantity).toBe(3);
    expect(order().items[0].itemTotal).toBe(15);
    expect(order().subtotal).toBe(15);
  });

  it('keeps differing modifier sets on separate lines', () => {
    const oat = { id: 'm1', name: 'Oat milk', price: 1 };
    usePOSStore.getState().addItemToCart(coffee, [oat]);
    usePOSStore.getState().addItemToCart(coffee, []);
    expect(order().items).toHaveLength(2);
    // 5 + 1 modifier, then plain 5
    expect(order().subtotal).toBe(11);
  });

  it('folds a repeat with the SAME modifiers regardless of order', () => {
    const a = { id: 'a', name: 'A', price: 1 };
    const b = { id: 'b', name: 'B', price: 2 };
    usePOSStore.getState().addItemToCart(pizza, [a, b]);
    usePOSStore.getState().addItemToCart(pizza, [b, a]); // same set, different order
    expect(order().items).toHaveLength(1);
    expect(order().items[0].quantity).toBe(2);
    // (10 + 1 + 2) * 2
    expect(order().items[0].itemTotal).toBe(26);
  });

  it('never merges weight-based items — each weigh-in is its own line', () => {
    usePOSStore.getState().addItemToCart(coffee, [], 1, { weightAmount: 2 });
    usePOSStore.getState().addItemToCart(coffee, [], 1, { weightAmount: 2 });
    expect(order().items).toHaveLength(2);
    // price * weight * qty = 5 * 2 * 1 each
    expect(order().items[0].itemTotal).toBe(10);
    expect(order().subtotal).toBe(20);
  });

  it('applies a portion multiplier to the line total', () => {
    usePOSStore.getState().addItemToCart(coffee, [], 1, { portionMultiplier: 0.5 });
    expect(order().items[0].itemTotal).toBe(2.5);
    expect(order().subtotal).toBe(2.5);
  });

  it('keeps different variants of the same product on separate lines', () => {
    usePOSStore.getState().addItemToCart(coffee, [], 1, { variantId: 'A', variantName: 'Large' });
    usePOSStore.getState().addItemToCart(coffee, [], 1, { variantId: 'B', variantName: 'Small' });
    expect(order().items).toHaveLength(2);
  });

  it('merges bundles by bundleId', () => {
    const bundle = { id: 99, name: 'Combo', price: 12, isBundle: true, bundleId: 7 };
    usePOSStore.getState().addItemToCart(bundle);
    usePOSStore.getState().addItemToCart(bundle);
    expect(order().items).toHaveLength(1);
    expect(order().items[0].quantity).toBe(2);
    expect(order().items[0].isBundle).toBe(true);
    expect(order().items[0].bundleId).toBe(7);
  });

  it('includes an active service-fee percent in the recomputed total', () => {
    resetOrder({ serviceFeePercent: 10 });
    usePOSStore.getState().addItemToCart(pizza); // subtotal 10
    expect(order().serviceFee).toBe(1); // 10% of 10
    expect(order().total).toBe(11);
  });
});

describe('updateItemQuantity', () => {
  it('recomputes the line and order totals', () => {
    usePOSStore.getState().addItemToCart(pizza);
    const id = order().items[0].id;
    usePOSStore.getState().updateItemQuantity(id, 4);
    expect(order().items[0].quantity).toBe(4);
    expect(order().items[0].itemTotal).toBe(40);
    expect(order().subtotal).toBe(40);
    expect(order().total).toBe(40);
  });

  it('scales a weight line by weight * quantity', () => {
    usePOSStore.getState().addItemToCart(coffee, [], 1, { weightAmount: 2 }); // 5*2*1 = 10
    const id = order().items[0].id;
    usePOSStore.getState().updateItemQuantity(id, 3); // 5 * 2 * 3
    expect(order().items[0].itemTotal).toBe(30);
  });
});

describe('updateItemWeight / updateItemPortion', () => {
  it('updateItemWeight recomputes itemTotal = fullPrice * weight * qty', () => {
    usePOSStore.getState().addItemToCart(coffee, [], 1, { weightAmount: 1 });
    const id = order().items[0].id;
    usePOSStore.getState().updateItemWeight(id, 3);
    expect(order().items[0].weightAmount).toBe(3);
    expect(order().items[0].itemTotal).toBe(15);
    expect(order().subtotal).toBe(15);
  });

  it('updateItemPortion recomputes itemTotal = fullPrice * portion * qty', () => {
    usePOSStore.getState().addItemToCart(pizza, [], 2); // qty 2
    const id = order().items[0].id;
    usePOSStore.getState().updateItemPortion(id, 0.5);
    expect(order().items[0].portionMultiplier).toBe(0.5);
    expect(order().items[0].itemTotal).toBe(10); // 10 * 0.5 * 2
  });
});

describe('removeItemFromCart', () => {
  it('removes a normal line and recomputes', () => {
    usePOSStore.getState().addItemToCart(coffee);
    usePOSStore.getState().addItemToCart(pizza);
    const coffeeId = order().items.find((i) => i.name === 'Coffee').id;
    usePOSStore.getState().removeItemFromCart(coffeeId);
    expect(order().items).toHaveLength(1);
    expect(order().subtotal).toBe(10);
  });

  it('resets the discount and promotion when the removed line is a free item', async () => {
    usePOSStore.getState().addItemToCart(pizza); // subtotal 10
    const { freeItem } = await usePOSStore.getState().addFreeItem({
      productId: 9,
      productName: 'Cookie',
      price: 3,
      couponCode: 'FREEBIE',
      promotionId: 7,
      promotionName: 'Promo',
    });
    expect(order().discount).toBe(3);
    expect(order().discountType).toBe('FREE_ITEM');
    expect(order().total).toBe(7); // 10 - 3

    usePOSStore.getState().removeItemFromCart(freeItem.id);
    expect(order().discount).toBe(0);
    expect(order().discountType).toBeNull();
    expect(order().couponCode).toBeNull();
    expect(order().promotionId).toBeNull();
    expect(order().total).toBe(10); // discount cleared
  });
});

describe('addFreeItem', () => {
  it('adds a zero-priced line and records the promotion', async () => {
    const res = await usePOSStore.getState().addFreeItem({
      productId: 9,
      productName: 'Cookie',
      price: 3,
      couponCode: 'X',
      promotionId: 1,
      promotionName: 'P',
    });
    expect(res.success).toBe(true);
    expect(order().items).toHaveLength(1);
    expect(order().items[0].itemTotal).toBe(0);
    expect(order().items[0].name).toBe('Cookie (FREE)');
    expect(order().couponCode).toBe('X');
  });

  it('refuses a duplicate free item for the same product', async () => {
    const data = { productId: 9, productName: 'Cookie', price: 3, couponCode: 'X' };
    await usePOSStore.getState().addFreeItem(data);
    const second = await usePOSStore.getState().addFreeItem(data);
    expect(second.success).toBe(false);
    expect(order().items).toHaveLength(1);
  });
});

describe('fee actions', () => {
  it('setServiceFee derives fee from subtotal and folds it into the total', () => {
    resetOrder({ subtotal: 200, total: 200 });
    usePOSStore.getState().setServiceFee(7.5);
    expect(order().serviceFeePercent).toBe(7.5);
    expect(order().serviceFee).toBe(15); // 7.5% of 200
    expect(order().total).toBe(215);
  });

  it('setEntryFee adds a flat amount to the total', () => {
    resetOrder({ subtotal: 50, total: 50 });
    usePOSStore.getState().setEntryFee(8);
    expect(order().entryFee).toBe(8);
    expect(order().total).toBe(58);
  });
});

describe('total is clamped at zero', () => {
  it('never goes negative when a discount exceeds the subtotal', () => {
    resetOrder({ subtotal: 5, discount: 100, total: 5 });
    // Any recomputing action re-derives the clamped total; setServiceFee(0) keeps subtotal at 5.
    usePOSStore.getState().setServiceFee(0);
    expect(order().total).toBe(0); // max(0, 5 + 0 + 0 + 0 - 100)
  });
});

describe('clearCart', () => {
  it('empties items and drops the total to just the delivery fee', () => {
    resetOrder({ deliveryFee: 3 });
    usePOSStore.getState().addItemToCart(coffee);
    usePOSStore.getState().setServiceFee(10);
    usePOSStore.getState().clearCart();

    expect(order().items).toEqual([]);
    expect(order().subtotal).toBe(0);
    expect(order().serviceFeePercent).toBe(0);
    expect(order().serviceFee).toBe(0);
    expect(order().discount).toBe(0);
    expect(order().discountType).toBeNull();
    expect(order().total).toBe(3); // delivery fee only
  });
});

describe('updateOrderNotes', () => {
  it('sets the order note without touching totals', () => {
    usePOSStore.getState().addItemToCart(coffee);
    usePOSStore.getState().updateOrderNotes('No sugar');
    expect(order().notes).toBe('No sugar');
    expect(order().subtotal).toBe(5);
  });
});

// PaymentScreen orchestrates these but computes none of them itself — the money math lives here, so
// this is where it is pinned (change-due and the split-bill flow) without rendering the screen.
describe('setAmountTendered (change due)', () => {
  it('returns change when the tender exceeds the total', () => {
    resetOrder({ total: 20 });
    usePOSStore.getState().setAmountTendered(50);
    expect(payment().amountTendered).toBe(50);
    expect(payment().changeDue).toBe(30);
  });

  it('gives exact change of zero when tender equals the total', () => {
    resetOrder({ total: 20 });
    usePOSStore.getState().setAmountTendered(20);
    expect(payment().changeDue).toBe(0);
  });

  it('never shows negative change when the tender is short', () => {
    resetOrder({ total: 20 });
    usePOSStore.getState().setAmountTendered(15);
    expect(payment().amountTendered).toBe(15);
    expect(payment().changeDue).toBe(0);
  });
});

describe('payment method/status', () => {
  it('records the selected method and status', () => {
    usePOSStore.getState().setPaymentMethod('CASH');
    usePOSStore.getState().setPaymentStatus('COMPLETED');
    expect(payment().method).toBe('CASH');
    expect(payment().status).toBe('COMPLETED');
  });
});

describe('split-bill flow', () => {
  const makeResult = () => ({
    orderId: 42,
    orderNumber: 'ORD-20260101-0007',
    splits: [
      { amount: 10, personNumber: 1 },
      { amount: 15, personNumber: 2 },
    ],
  });

  it('setSplitBillResult activates split mode with nothing paid yet', () => {
    usePOSStore.getState().setSplitBillResult(makeResult(), 25);
    expect(splitBill().active).toBe(true);
    expect(splitBill().originalOrderTotal).toBe(25);
    expect(splitBill().paidSplits).toEqual([]);
    expect(usePOSStore.getState().isAllSplitsPaid()).toBe(false);
  });

  it('startPayingSplit loads that split amount as the order total and resets fees', () => {
    usePOSStore.getState().setSplitBillResult(makeResult(), 25);
    usePOSStore.getState().startPayingSplit(0);

    expect(splitBill().currentSplitIndex).toBe(0);
    expect(order().total).toBe(10);
    expect(order().subtotal).toBe(10);
    expect(order().serviceFee).toBe(0);
    expect(order().deliveryFee).toBe(0);
    expect(order().id).toBe(42);
    expect(order().orderNumber).toBe('ORD-20260101-0007');
    expect(usePOSStore.getState().ui.currentScreen).toBe('payment');
  });

  it('startPayingSplit is a no-op for an out-of-range index', () => {
    usePOSStore.getState().setSplitBillResult(makeResult(), 25);
    usePOSStore.getState().startPayingSplit(99);
    expect(splitBill().currentSplitIndex).toBeNull(); // unchanged
  });

  it('marks each split paid and flips isAllSplitsPaid only once every split is settled', () => {
    usePOSStore.getState().setSplitBillResult(makeResult(), 25);

    usePOSStore.getState().markSplitAsPaid(0, 'CASH');
    expect(splitBill().paidSplits).toEqual([0]);
    expect(splitBill().result.splits[0].paid).toBe(true);
    expect(splitBill().result.splits[0].paymentMethod).toBe('CASH');
    expect(splitBill().currentSplitIndex).toBeNull();
    expect(usePOSStore.getState().isAllSplitsPaid()).toBe(false); // one still owing

    usePOSStore.getState().markSplitAsPaid(1, 'CARD');
    expect(splitBill().paidSplits).toEqual([0, 1]);
    expect(usePOSStore.getState().isAllSplitsPaid()).toBe(true);
  });

  it('clearSplitBill tears the split state back down', () => {
    usePOSStore.getState().setSplitBillResult(makeResult(), 25);
    usePOSStore.getState().clearSplitBill();
    expect(splitBill().active).toBe(false);
    expect(splitBill().result).toBeNull();
    expect(splitBill().paidSplits).toEqual([]);
    expect(usePOSStore.getState().isAllSplitsPaid()).toBe(false);
  });
});

describe('submitToKitchen', () => {
  it('refuses an order that has not been created yet (temp id)', async () => {
    resetOrder({ id: 'temp-123', orderNumber: null });

    const result = await usePOSStore.getState().submitToKitchen();

    expect(result.success).toBe(false);
  });

  it('fires the order to the kitchen and refreshes the kitchen status', async () => {
    posAPI.submitToKitchen = vi.fn().mockResolvedValue({ data: { data: {} } });
    posAPI.getKitchenStatus = vi.fn().mockResolvedValue({
      data: { data: { orderId: 5, kitchenOrderId: 9, kitchenStatus: 'PENDING', orderStatus: 'PREPARING' } },
    });
    resetOrder({ id: 5, orderNumber: 'ORD-5' });

    const result = await usePOSStore.getState().submitToKitchen();

    expect(result.success).toBe(true);
    expect(posAPI.submitToKitchen).toHaveBeenCalledWith(5);
    // The refreshed status is what the confirmation screen renders and what hides the button.
    expect(usePOSStore.getState().kitchenStatus.status).toBe('PENDING');
    expect(usePOSStore.getState().kitchenStatus.orderStatus).toBe('PREPARING');
  });

  it('surfaces the backend error when the order cannot be sent', async () => {
    posAPI.submitToKitchen = vi.fn().mockRejectedValue({
      response: { data: { message: 'Order is not open for the kitchen (status: COMPLETED)' } },
    });
    resetOrder({ id: 7, orderNumber: 'ORD-7' });

    const result = await usePOSStore.getState().submitToKitchen();

    expect(result.success).toBe(false);
    expect(result.error).toContain('not open');
  });
});
