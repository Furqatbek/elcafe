import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCustomer } from './CustomerContext';
import { selfServiceAPI } from '../../services/api';
import {
  ChevronLeft,
  UtensilsCrossed,
  ShoppingBag,
  AlertCircle,
  User,
  Phone,
  MessageSquare,
  CheckCircle,
  Tag,
  X,
  Loader2,
} from 'lucide-react';

export default function CheckoutPage() {
  const navigate = useNavigate();
  const { session, cart, submitOrder } = useCustomer();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [orderType, setOrderType] = useState('DINE_IN');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [notes, setNotes] = useState('');

  // Coupon state
  const [couponCode, setCouponCode] = useState('');
  const [couponValidating, setCouponValidating] = useState(false);
  const [couponError, setCouponError] = useState(null);
  const [appliedCoupon, setAppliedCoupon] = useState(null);

  const formatPrice = (price) => {
    return new Intl.NumberFormat('uz-UZ').format(price) + ' UZS';
  };

  const handleValidateCoupon = async () => {
    if (!couponCode.trim()) return;

    setCouponValidating(true);
    setCouponError(null);

    try {
      const response = await selfServiceAPI.validateCoupon(
        session.restaurantId,
        couponCode.trim(),
        cart.total,
        null
      );

      const data = response.data?.data || response.data;

      if (data.valid) {
        setAppliedCoupon({
          code: couponCode.trim(),
          promotionName: data.promotionName,
          discount: data.calculatedDiscount || 0,
        });
        setCouponCode('');
      } else {
        setCouponError(data.errorMessage || 'Invalid coupon code');
      }
    } catch (err) {
      setCouponError(err.response?.data?.message || 'Failed to validate coupon');
    } finally {
      setCouponValidating(false);
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
    setCouponError(null);
  };

  const getFinalTotal = () => {
    const discount = appliedCoupon?.discount || 0;
    return Math.max(0, cart.total - discount);
  };

  const handleSubmit = async () => {
    setError(null);
    setSubmitting(true);

    try {
      const result = await submitOrder({
        orderType,
        customerName: customerName || null,
        customerPhone: customerPhone || null,
        notes: notes || null,
        couponCode: appliedCoupon?.code || null,
      });
      navigate(`/${result.orderId}/status`, { replace: true });
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to submit order');
    } finally {
      setSubmitting(false);
    }
  };

  if (!session) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
          <AlertCircle className="w-12 h-12 mx-auto mb-4 text-orange-500" />
          <h2 className="text-xl font-semibold text-gray-800 mb-2">No Active Session</h2>
          <p className="text-gray-600 mb-4">Please scan the QR code at your table to start ordering.</p>
        </div>
      </div>
    );
  }

  if (cart.items.length === 0) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
          <ShoppingBag className="w-12 h-12 mx-auto mb-4 text-gray-300" />
          <h2 className="text-xl font-semibold text-gray-800 mb-2">Cart is Empty</h2>
          <p className="text-gray-600 mb-4">Add some items to your cart before checkout.</p>
          <button
            onClick={() => navigate(-1)}
            className="bg-blue-600 text-white px-6 py-3 rounded-lg font-medium hover:bg-blue-700"
          >
            Browse Menu
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-32">
      {/* Header */}
      <div className="bg-white shadow-sm sticky top-0 z-40">
        <div className="max-w-lg mx-auto px-4 py-3">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate(-1)}
              className="flex items-center gap-2 text-gray-600"
            >
              <ChevronLeft className="w-5 h-5" />
            </button>
            <h1 className="text-lg font-bold text-gray-900">Checkout</h1>
          </div>
        </div>
      </div>

      <div className="max-w-lg mx-auto px-4 py-4 space-y-4">
        {/* Error Message */}
        {error && (
          <div className="bg-red-50 text-red-700 rounded-lg p-3 flex items-center gap-2">
            <AlertCircle className="w-5 h-5 flex-shrink-0" />
            <span>{error}</span>
            <button onClick={() => setError(null)} className="ml-auto">
              &times;
            </button>
          </div>
        )}

        {/* Table Info */}
        {session.tableNumber && (
          <div className="bg-blue-50 rounded-lg p-4 flex items-center gap-3">
            <UtensilsCrossed className="w-6 h-6 text-blue-600" />
            <div>
              <p className="text-sm text-blue-600">Your Table</p>
              <p className="font-bold text-blue-800">Table {session.tableNumber}</p>
            </div>
          </div>
        )}

        {/* Order Type */}
        <div className="bg-white rounded-lg shadow-sm p-4">
          <h2 className="font-semibold text-gray-900 mb-3">Order Type</h2>
          <div className="grid grid-cols-2 gap-3">
            <button
              onClick={() => setOrderType('DINE_IN')}
              className={`p-4 rounded-lg border-2 text-center transition-colors ${
                orderType === 'DINE_IN'
                  ? 'border-blue-600 bg-blue-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <UtensilsCrossed className={`w-6 h-6 mx-auto mb-2 ${
                orderType === 'DINE_IN' ? 'text-blue-600' : 'text-gray-400'
              }`} />
              <span className={`font-medium ${
                orderType === 'DINE_IN' ? 'text-blue-600' : 'text-gray-700'
              }`}>Dine In</span>
            </button>
            <button
              onClick={() => setOrderType('TAKEAWAY')}
              className={`p-4 rounded-lg border-2 text-center transition-colors ${
                orderType === 'TAKEAWAY'
                  ? 'border-blue-600 bg-blue-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <ShoppingBag className={`w-6 h-6 mx-auto mb-2 ${
                orderType === 'TAKEAWAY' ? 'text-blue-600' : 'text-gray-400'
              }`} />
              <span className={`font-medium ${
                orderType === 'TAKEAWAY' ? 'text-blue-600' : 'text-gray-700'
              }`}>Takeaway</span>
            </button>
          </div>
        </div>

        {/* Customer Info (Optional) */}
        <div className="bg-white rounded-lg shadow-sm p-4">
          <h2 className="font-semibold text-gray-900 mb-3">Your Details (Optional)</h2>
          <div className="space-y-3">
            <div className="relative">
              <User className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
              <input
                type="text"
                placeholder="Your name"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                className="w-full pl-10 pr-4 py-3 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <div className="relative">
              <Phone className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
              <input
                type="tel"
                placeholder="Phone number"
                value={customerPhone}
                onChange={(e) => setCustomerPhone(e.target.value)}
                className="w-full pl-10 pr-4 py-3 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>
        </div>

        {/* Special Instructions */}
        <div className="bg-white rounded-lg shadow-sm p-4">
          <h2 className="font-semibold text-gray-900 mb-3">Order Notes (Optional)</h2>
          <div className="relative">
            <MessageSquare className="absolute left-3 top-3 text-gray-400 w-5 h-5" />
            <textarea
              placeholder="Any special requests for your order?"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full pl-10 pr-4 py-3 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 resize-none"
              rows={3}
            />
          </div>
        </div>

        {/* Coupon Code */}
        <div className="bg-white rounded-lg shadow-sm p-4">
          <h2 className="font-semibold text-gray-900 mb-3">Coupon Code</h2>
          {appliedCoupon ? (
            <div className="flex items-center justify-between bg-green-50 border border-green-200 rounded-lg p-3">
              <div className="flex items-center gap-2">
                <Tag className="w-5 h-5 text-green-600" />
                <div>
                  <p className="font-medium text-green-800">{appliedCoupon.code}</p>
                  <p className="text-sm text-green-600">
                    {appliedCoupon.promotionName} - Save {formatPrice(appliedCoupon.discount)}
                  </p>
                </div>
              </div>
              <button
                onClick={handleRemoveCoupon}
                className="p-1 hover:bg-green-100 rounded-full"
              >
                <X className="w-5 h-5 text-green-600" />
              </button>
            </div>
          ) : (
            <div className="space-y-2">
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Tag className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
                  <input
                    type="text"
                    placeholder="Enter coupon code"
                    value={couponCode}
                    onChange={(e) => setCouponCode(e.target.value.toUpperCase())}
                    onKeyDown={(e) => e.key === 'Enter' && handleValidateCoupon()}
                    className="w-full pl-10 pr-4 py-3 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  />
                </div>
                <button
                  onClick={handleValidateCoupon}
                  disabled={couponValidating || !couponCode.trim()}
                  className="px-4 py-3 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                >
                  {couponValidating ? (
                    <Loader2 className="w-5 h-5 animate-spin" />
                  ) : (
                    'Apply'
                  )}
                </button>
              </div>
              {couponError && (
                <p className="text-sm text-red-600 flex items-center gap-1">
                  <AlertCircle className="w-4 h-4" />
                  {couponError}
                </p>
              )}
            </div>
          )}
        </div>

        {/* Order Summary */}
        <div className="bg-white rounded-lg shadow-sm p-4">
          <h2 className="font-semibold text-gray-900 mb-3">Order Summary</h2>
          <div className="space-y-2">
            {cart.items.map((item) => (
              <div key={item.id} className="flex justify-between text-sm">
                <span className="text-gray-600">
                  {item.quantity}x {item.productName}
                  {item.variantName && ` (${item.variantName})`}
                </span>
                <span className="font-medium">{formatPrice(item.totalPrice)}</span>
              </div>
            ))}
            <div className="border-t pt-2 mt-2 space-y-1">
              <div className="flex justify-between text-sm">
                <span className="text-gray-600">Subtotal</span>
                <span>{formatPrice(cart.total)}</span>
              </div>
              {appliedCoupon && (
                <div className="flex justify-between text-sm text-green-600">
                  <span>Discount ({appliedCoupon.code})</span>
                  <span>-{formatPrice(appliedCoupon.discount)}</span>
                </div>
              )}
              <div className="flex justify-between font-bold text-lg pt-1">
                <span>Total</span>
                <span className="text-blue-600">{formatPrice(getFinalTotal())}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Submit Button */}
      <div className="fixed bottom-0 left-0 right-0 bg-white border-t shadow-lg z-50">
        <div className="max-w-lg mx-auto px-4 py-4">
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="w-full bg-blue-600 text-white rounded-lg py-4 font-semibold hover:bg-blue-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {submitting ? (
              <>
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                Placing Order...
              </>
            ) : (
              <>
                <CheckCircle className="w-5 h-5" />
                Place Order - {formatPrice(getFinalTotal())}
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
