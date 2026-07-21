# Restaurant POS System

A modern, touch-optimized Point of Sale (POS) system built with React, designed specifically for tablet devices in restaurant environments.

## Features

### 🎯 Core Functionality
- **Multi-Order Type Support**: Delivery, Takeaway, and Dine-in orders
- **Touch-Optimized Interface**: 48px+ touch targets, haptic feedback, gesture support
- **Real-time Cart Management**: Add, edit, remove items with live total calculation
- **Product Customization**: Modifiers, add-ons, size variants, special instructions
- **Multiple Payment Methods**: Cash (with change calculator), Card, Mobile Pay
- **Kitchen Ticket Printing**: Thermal printer-friendly receipts
- **Offline Capable**: Local state persistence with localStorage

### 🎨 Design System
- **Color Palette**: Primary (Blue), Success (Green), Warning (Amber), Danger (Red)
- **Typography**: System font stack with responsive sizing (12px - 60px)
- **Spacing**: 4px grid system (0.25rem - 6rem)
- **Touch Targets**: Small (48px), Medium (56px), Large (64px), XL (80px)
- **Responsive**: Optimized for tablets (768px - 1280px)

## Architecture

### Entry points & routing

The POS has **two implementations**, both mounted from the admin app's router (`src/App.jsx`) and
wrapped in `PrivateRoute` (login required):

- **`single/SinglePagePOS.jsx`** — the current default, rendered at **`/pos`**. A single-page POS with
  its own Zustand store (`single/store.js`, persisted to `localStorage`), theme (`single/theme.js`),
  and component set under `single/components/`.
- **`POSApp.jsx`** — the older multi-screen flow, now only at **`/pos/legacy`**.

Two auxiliary full-screen views are routed directly from `screens/`:
`CustomerDisplayScreen` (`/pos/customer-display`) and `OrderStatusBoardScreen` (`/pos/order-status`).

> `KitchenTicketPage.jsx` exists in the source but is **not wired into the router** — there is no
> route that renders it. Kitchen tickets are produced by the `KitchenTicket` component inside the
> order flow.

### Directory Structure
```
src/pos/
├── POSApp.jsx                 # Legacy multi-screen POS (mounted at /pos/legacy)
├── KitchenTicketPage.jsx      # Kitchen ticket print page — present but NOT routed
├── config/
│   └── designSystem.js        # Design tokens and theme configuration
├── store/
│   └── posStore.js            # Zustand store for the legacy POSApp
├── components/                # Shared POS components (TouchButton, ProductCard, CartItem,
│   │                          #   NumericKeypad, POSModal, KitchenTicket, payment dialogs,
│   │                          #   table/floor-plan + split-bill/refund/tip widgets, …)
│   └── …
├── screens/                   # Legacy POSApp screens + the two routed full-screen views:
│   │                          #   StartOrderScreen, MenuSelectionScreen, ProductModifiersScreen,
│   │                          #   CartScreen, OrderDetailsScreen, PaymentScreen,
│   │                          #   OrderConfirmationScreen, ActiveOrdersScreen, SplitBillScreen,
│   │                          #   TableSelectionScreen, OrderModificationScreen,
│   │                          #   CustomerDisplayScreen (/pos/customer-display),
│   │                          #   OrderStatusBoardScreen (/pos/order-status)
│   └── …
└── single/                    # Default single-page POS (mounted at /pos)
    ├── SinglePagePOS.jsx      # Single-page POS root
    ├── store.js               # Zustand store (persisted to localStorage)
    ├── theme.js               # Theme tokens for the single-page POS
    └── components/            # Its own components (Header, CategoriesRail, ProductGrid,
                               #   ProductTile, CartLine, TicketRail, PaymentBlock, ModifierEditor,
                               #   TypeSegments, TypeFields, Button, Divider)
```

## User Flows

### 1. Delivery Order Flow
1. **Start Order** → Select "Delivery"
2. **Menu Selection** → Browse categories, search, select products
3. **Customize Product** → Add modifiers, adjust quantity
4. **Review Cart** → Edit items, add order notes
5. **Order Details** → Enter customer info, delivery address
6. **Payment** → Select payment method, process payment
7. **Confirmation** → Print tickets, start new order

### 2. Takeaway Order Flow
1. **Start Order** → Select "Takeaway"
2. **Menu Selection** → Browse and select items
3. **Review Cart** → Verify order
4. **Order Details** → Customer name and phone
5. **Payment** → Process payment
6. **Confirmation** → Print receipt and kitchen ticket

### 3. Dine-in Order Flow
1. **Start Order** → Select "Dine In"
2. **Menu Selection** → Select items
3. **Review Cart** → Add items
4. **Order Details** → Table number, guest count
5. **Payment** → Process payment
6. **Confirmation** → Send to kitchen

## State Management

### POS Store (Zustand)
```javascript
{
  currentOrder: {
    id, type, items, subtotal, tax, deliveryFee, total, notes
  },
  customer: {
    id, name, phone, email, address, tableNumber, guestCount
  },
  payment: {
    method, amountTendered, changeDue, status
  },
  ui: {
    currentScreen, isLoading, error, selectedCategory, selectedProduct
  },
  menu: {
    categories, products, lastFetched
  }
}
```

### Actions
- `startNewOrder(type)` - Initialize new order
- `addItemToCart(product, modifiers, quantity)` - Add item
- `updateItemQuantity(itemId, quantity)` - Update quantity
- `removeItemFromCart(itemId)` - Remove item
- `setCustomerInfo(customer)` - Save customer details
- `setPaymentMethod(method)` - Set payment type
- `completeOrder()` - Finalize and reset
- `resetPOS()` - Clear all state

## Components

### TouchButton
Touch-optimized button with haptic feedback.
```jsx
<TouchButton
  variant="primary" // primary|secondary|success|danger|warning|outline|ghost
  size="medium"     // small|medium|large|xl
  fullWidth={false}
  loading={false}
  disabled={false}
  icon={<Icon />}
  onClick={handleClick}
>
  Button Text
</TouchButton>
```

### ProductCard
Product display with image, price, availability.
```jsx
<ProductCard
  product={{
    id, name, price, description, imageUrl, available, category
  }}
  onSelect={handleSelect}
/>
```

### CartItem
Cart item with quantity controls and remove option.
```jsx
<CartItem
  item={{
    id, name, basePrice, modifiers, quantity, itemTotal, notes
  }}
  onUpdateQuantity={handleUpdate}
  onRemove={handleRemove}
/>
```

### NumericKeypad
Touch-friendly numeric input for cash payments.
```jsx
<NumericKeypad
  value={amount}
  onValueChange={setValue}
  label="Amount Tendered"
  allowDecimal={true}
  maxLength={10}
/>
```

## API Integration

### Menu Data
```javascript
// Fetch categories
GET /api/v1/categories

// Fetch products (scoped to the current restaurant)
GET /api/v1/products/restaurant/{restaurantId}
```

### Order Submission
```javascript
// Create new order (POS surface — NOT the customer self-service /consumer/orders endpoint)
POST /api/v1/pos/orders
{
  type: 'DELIVERY' | 'TAKEAWAY' | 'DINE_IN',
  customerId, customerName, customerPhone, customerEmail,
  items: [{ productId, quantity, price, modifiers, notes }],
  subtotal, tax, deliveryFee, total,
  paymentMethod: 'CASH' | 'CARD' | 'MOBILE',
  paymentStatus: 'PAID',
  deliveryAddress, tableNumber, guestCount
}
```

## Access & Usage

### URL Access

The dev server runs on port **3000** and the app is served under the `/admin` base path
(`vite base: '/admin/'` + `<BrowserRouter basename="/admin">`). All POS routes require a logged-in
session (`PrivateRoute`):

- **POS Interface (default single-page)**: `http://localhost:3000/admin/pos`
- **POS Interface (legacy multi-screen)**: `http://localhost:3000/admin/pos/legacy`
- **Customer Display**: `http://localhost:3000/admin/pos/customer-display`
- **Order Status Board**: `http://localhost:3000/admin/pos/order-status`

> There is no `/pos/kitchen-ticket` route — `KitchenTicketPage.jsx` is not wired into the router.

### Development
```bash
cd frontend
npm install
npm run dev
```

### Production Build
```bash
npm run build
```

## Touch Optimization

### Design Principles
1. **Minimum 48px touch targets** for all interactive elements
2. **Haptic feedback** on button presses (10ms vibration)
3. **Large, clear text** (minimum 14px, recommended 16px+)
4. **Generous spacing** between touch targets (8px minimum)
5. **Visual feedback** on touch (scale, color change)
6. **No hover states** - all interactions work without hover
7. **Keyboard-free operation** - on-screen numeric keypad for numbers

### Accessibility
- WCAG 2.1 AA compliant touch targets
- High contrast colors (4.5:1 minimum)
- Focus indicators for keyboard navigation
- Screen reader compatible
- Error messages clearly displayed

## Performance

### Optimization Strategies
- Lazy loading for menu images
- Debounced search input
- Memoized product filtering
- Local state caching with 5-minute TTL
- Virtualized long lists (if needed)
- Optimistic UI updates

## Printing

### Kitchen Ticket
- **Format**: Thermal printer (80mm width)
- **Content**: Order number, items, modifiers, notes, table/delivery info
- **Auto-print**: Opens in new window and triggers print dialog
- **Monospace font**: For clear alignment and readability

### Customer Receipt
- Standard print dialog
- Order summary with totals
- Customer information
- Payment details

## Browser Support
- **Chrome/Edge**: Full support (recommended)
- **Safari**: Full support
- **Firefox**: Full support
- **Mobile browsers**: Touch events fully supported

## Security Considerations
- **Authentication required**: every `/pos` route is wrapped in `PrivateRoute`, so a valid login/JWT
  session is needed to reach the POS (operator-facing, but not unauthenticated)
- Should be deployed on internal network
- Payment data not stored locally
- Session data cleared after order completion
- XSS protection via React's built-in sanitization

## Future Enhancements
- [ ] Split payment support
- [ ] Discount/coupon application
- [ ] Customer loyalty integration
- [ ] Order history and reorder
- [ ] Multi-language support
- [ ] Offline order queuing
- [ ] Receipt email option
- [ ] Tip calculation for dine-in
- [ ] Table management integration

## Support
For issues or feature requests, contact the development team.
