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

### Directory Structure
```
src/pos/
├── POSApp.jsx                 # Main POS application with routing
├── KitchenTicketPage.jsx      # Standalone kitchen ticket print page
├── config/
│   └── designSystem.js        # Design tokens and theme configuration
├── store/
│   └── posStore.js            # Zustand state management
├── components/
│   ├── TouchButton.jsx        # Touch-optimized button component
│   ├── ProductCard.jsx        # Product display card
│   ├── CartItem.jsx           # Cart item with quantity controls
│   ├── NumericKeypad.jsx      # Touch-friendly numeric input
│   ├── POSModal.jsx           # Full-screen modal dialog
│   └── KitchenTicket.jsx      # Kitchen ticket print template
└── screens/
    ├── StartOrderScreen.jsx   # Order type selection
    ├── MenuSelectionScreen.jsx# Browse menu & select products
    ├── ProductModifiersScreen.jsx # Customize product
    ├── CartScreen.jsx         # Review order & edit items
    ├── OrderDetailsScreen.jsx # Customer info & delivery details
    ├── PaymentScreen.jsx      # Payment processing
    └── OrderConfirmationScreen.jsx # Success confirmation
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

// Fetch products
GET /api/v1/products
```

### Order Submission
```javascript
// Create new order
POST /api/v1/consumer/orders
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
- **POS Interface**: `http://localhost:5173/pos`
- **Kitchen Ticket Print**: `http://localhost:5173/pos/kitchen-ticket`

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
- No authentication required (operator-facing only)
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
