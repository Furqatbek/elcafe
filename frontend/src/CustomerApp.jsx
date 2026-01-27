import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { CustomerProvider, MenuPage, CartPage, CheckoutPage, OrderStatusPage, OrderTrackingPage, ReservationPage, RestaurantSelectPage } from './pages/customer';

function CustomerApp() {
  return (
    <BrowserRouter basename="/order">
      <CustomerProvider>
        <Routes>
          {/* Menu page - entry point from QR code */}
          <Route path="/menu/:restaurantId/:tableCode" element={<MenuPage />} />

          {/* Cart */}
          <Route path="/cart" element={<CartPage />} />

          {/* Checkout */}
          <Route path="/checkout" element={<CheckoutPage />} />

          {/* Order Status */}
          <Route path="/:orderId/status" element={<OrderStatusPage />} />

          {/* Order Tracking - public tracking page */}
          <Route path="/track/:orderNumber" element={<OrderTrackingPage />} />

          {/* Reservations - restaurant selection page */}
          <Route path="/reserve" element={<RestaurantSelectPage />} />

          {/* Reservations - specific restaurant */}
          <Route path="/reserve/:restaurantId" element={<ReservationPage />} />

          {/* Fallback */}
          <Route path="*" element={
            <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
              <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
                <h2 className="text-xl font-semibold text-gray-800 mb-2">Scan QR Code</h2>
                <p className="text-gray-600">Please scan the QR code at your table to start ordering.</p>
              </div>
            </div>
          } />
        </Routes>
      </CustomerProvider>
    </BrowserRouter>
  );
}

export default CustomerApp;
