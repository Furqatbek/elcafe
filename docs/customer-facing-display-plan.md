# Customer-Facing Display — Implementation Plan

> **STATUS: SHIPPED** — fully implemented (`pos/screens/CustomerDisplayScreen.jsx` + panels, route `/pos/customer-display`, localStorage sync). Kept as a historical design record.

## Concept

A second-screen display that faces the customer at the POS counter. While the cashier works on the main POS screen, the customer sees their order being built in real time on a separate monitor/tablet.

### Layout (70/30 split)

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│                    70% — RESTAURANT ADS                        │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                                                          │  │
│  │         Restaurant Logo + Name                           │  │
│  │                                                          │  │
│  │     [ Rotating promotional images / slides ]             │  │
│  │     - Today's specials                                   │  │
│  │     - Happy hour banner                                  │  │
│  │     - Social media QR code                               │  │
│  │     - Loyalty program info                               │  │
│  │                                                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
├────────────────────────────────────────────────────────────────┤
│                    30% — ORDER INFO                            │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Your Order                              items: 3         │  │
│  │──────────────────────────────────────────────────────────│  │
│  │ 2x  Shurva (0.5L)                            50,000     │  │
│  │ 1x  Manti (1 Portion)                        20,000     │  │
│  │ 1x  Coca-Cola                                  8,000     │  │
│  │──────────────────────────────────────────────────────────│  │
│  │                              Subtotal:        78,000     │  │
│  │                              Service (10%):    7,800     │  │
│  │                              ─────────────────────────   │  │
│  │                              TOTAL:           85,800     │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

When no active order exists, the order section shows a welcome message and the full screen is ads/branding.

---

## Architecture Decision: Shared Store vs Standalone Page

**Option A: POS internal screen** (via posStore `setCurrentScreen('customer-display')`)
- Pros: direct access to `currentOrder` state
- Cons: can't run on a separate browser window/tab; occupies the main POS

**Option B: Standalone route with polling** ← **RECOMMENDED**
- Route: `/admin/pos/customer-display?restaurant={id}`
- Runs in a **separate browser window/tab** on the customer-facing monitor
- Polls the POS state via a shared mechanism (localStorage events or API)
- Cashier's POS runs independently on the main monitor

**Sync mechanism: `localStorage` + `storage` event**
- POS store writes `currentOrder` to `localStorage` on every change
- Customer display listens for `storage` events → instant updates
- No backend changes needed, works across tabs on the same machine
- Fallback: periodic polling of localStorage every 500ms

---

## Phase 1: Planning & Design

### Data Flow

```
Cashier POS (Tab 1)                    Customer Display (Tab 2)
┌──────────────┐                       ┌──────────────────┐
│ posStore     │  localStorage write   │ useEffect listen │
│ addItemToCart├──────────────────────►│ storage event    │
│ removeItem   │  key: pos_customer_   │ parse + setState │
│ updateQty    │  display_order        │ render order     │
│ clearCart    │                       │                  │
└──────────────┘                       └──────────────────┘
```

### Files to Create

| # | File | Type |
|---|------|------|
| 1 | `frontend/src/pos/screens/CustomerDisplayScreen.jsx` | Main display page |
| 2 | `frontend/src/pos/components/CustomerOrderPanel.jsx` | Order info panel (30%) |
| 3 | `frontend/src/pos/components/CustomerAdsPanel.jsx` | Ads/branding panel (70%) |

### Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `frontend/src/pos/store/posStore.js` | Sync currentOrder to localStorage on changes |
| 2 | `frontend/src/App.jsx` | Add `/admin/pos/customer-display` route |
| 3 | `frontend/src/pos/screens/StartOrderScreen.jsx` | Add "Open Customer Display" button |
| 4 | `frontend/src/i18n/locales/en.json` | i18n keys |
| 5 | `frontend/src/i18n/locales/ru.json` | i18n keys |
| 6 | `frontend/src/i18n/locales/uz.json` | i18n keys |

> Note: `POSApp.jsx` does NOT need modification — the customer display runs as a standalone route (Option B), not as a POS internal screen.

---

## Phase 2: Sync Mechanism (posStore → localStorage)

### posStore.js — broadcast order state

Add a Zustand middleware/subscriber that writes to localStorage on every order change:

```javascript
// After store creation, subscribe to changes
posStore.subscribe(
  (state) => state.currentOrder,
  (currentOrder) => {
    localStorage.setItem('pos_customer_display_order', JSON.stringify({
      items: currentOrder.items,
      subtotal: currentOrder.subtotal,
      tax: currentOrder.tax,
      deliveryFee: currentOrder.deliveryFee,
      serviceFee: currentOrder.serviceFee,
      serviceFeePercent: currentOrder.serviceFeePercent,
      entryFee: currentOrder.entryFee,
      discount: currentOrder.discount,
      total: currentOrder.total,
      type: currentOrder.type,
      notes: currentOrder.notes,
      orderNumber: currentOrder.orderNumber,
      status: 'active',  // 'active' | 'completed' | 'idle'
      updatedAt: Date.now(),
    }));
  }
);
```

After `submitOrder` succeeds, broadcast completion with order number:
```javascript
localStorage.setItem('pos_customer_display_order', JSON.stringify({
  items: [],
  total: 0,
  orderNumber: response.data.orderNumber,
  status: 'completed',
  updatedAt: Date.now(),
}));
// Customer display shows "Thank you! Your order number is ORD-042" for 5s, then returns to idle
```

Restaurant info is fetched directly by the customer display via `restaurantAPI.getById(restaurantId)` on mount — no localStorage broadcast needed for this.

---

## Phase 3: Customer Display Page

### CustomerDisplayScreen.jsx

**Route:** `/admin/pos/customer-display?restaurant={id}`

**Behavior:**
- **Auto-fullscreen on mount** via `document.documentElement.requestFullscreen()` — hides browser tabs, address bar, and all chrome. Click anywhere to re-enter fullscreen if exited.
- Listens to `storage` events for real-time order updates
- Fetches restaurant details (name, logo) on mount
- Auto-hides cursor after 3s of inactivity (kiosk mode)
- Landscape layout: 70% left (ads) / 30% right (order)

**States:**
1. **Idle** (no order) — full-screen branding + welcome message
2. **Active order** — 70/30 split with live order items
3. **Order complete** — "Thank you! Your order number is {orderNumber}" animation, then back to idle after 5s

### CustomerAdsPanel.jsx (70% left)

- Restaurant logo (large, centered)
- Restaurant name
- Rotating slides (CSS animation, 5s intervals):
  - "Welcome to {restaurant}" hero
  - Today's specials (if available)
  - Social media / QR code slide
  - Generic branding images
- Configurable: restaurant can set ad images via settings (future)
- For now: static branding with restaurant name/logo + placeholder slides

### CustomerOrderPanel.jsx (30% right)

- Header: "Your Order" + item count
- Scrollable item list:
  - Item name (with variant name if applicable)
  - Quantity × unit price
  - Modifiers (smaller text below)
  - Line total
- Price summary:
  - Subtotal
  - Service fee (if > 0)
  - Delivery fee (if > 0)
  - Discount (if > 0, shown in green)
  - **Total** (large, bold)
- When empty: "Your order will appear here" message
- Animated item additions (slide-in from right)

---

## Phase 4: Route & Navigation

### App.jsx — add route

```jsx
<Route path="pos/customer-display" element={<CustomerDisplayScreen />} />
```

No auth guard — this page runs on the customer-facing monitor, doesn't need login.

### StartOrderScreen.jsx — add launch button

Add a small button in the header/settings area:
```jsx
<button onClick={() => window.open('/admin/pos/customer-display?restaurant=' + restaurantId, 'customer-display')}>
  Open Customer Display
</button>
```

The opened page auto-enters browser fullscreen via `requestFullscreen()` on mount — hides tabs, address bar, and all browser chrome. If the user accidentally exits fullscreen (e.g., presses Escape), clicking anywhere on the page re-enters fullscreen.

---

## Phase 5: Styling & UX

### Design Tokens

- Background: dark theme (dark gray/black) — looks professional on second screen
- Text: white/light for readability at distance
- Accent: restaurant brand color (or default blue)
- Font sizes: larger than POS (customer is further from screen)
  - Item names: 18-20px
  - Prices: 20-24px
  - Total: 32-40px bold
- No interactive elements — display only
- Smooth animations: item slide-in, total pulse on change

### Responsive

- Primary: landscape 1920×1080 (typical second monitor)
- Also works: 1280×720, tablet landscape
- 70/30 split adjusts to 60/40 on smaller screens

---

## Phase 6: i18n

### Translation Keys

```json
{
  "pos.customerDisplay.title": "Your Order",
  "pos.customerDisplay.welcome": "Welcome",
  "pos.customerDisplay.welcomeMessage": "Your order will appear here",
  "pos.customerDisplay.items": "items",
  "pos.customerDisplay.subtotal": "Subtotal",
  "pos.customerDisplay.serviceFee": "Service Fee",
  "pos.customerDisplay.deliveryFee": "Delivery Fee",
  "pos.customerDisplay.discount": "Discount",
  "pos.customerDisplay.total": "Total",
  "pos.customerDisplay.thankYou": "Thank you!",
  "pos.customerDisplay.orderNumber": "Your order number is {{orderNumber}}",
  "pos.customerDisplay.openDisplay": "Customer Display",
  "pos.customerDisplay.quantity": "×"
}
```

---

## Phase 7: Testing

### Manual Test Scenarios

1. Open POS → click "Customer Display" → second window opens with branding
2. Add items in POS → items appear in real-time on customer display
3. Change quantity → customer display updates
4. Remove item → customer display updates
5. Apply discount/coupon → totals update
6. Clear cart → customer display returns to idle/welcome state
7. Close POS tab → customer display shows last known state
8. Refresh customer display → loads current order from localStorage

---

## Implementation Order

| Step | Phase | Description |
|------|-------|-------------|
| 1 | Phase 2 | posStore localStorage sync |
| 2 | Phase 3 | CustomerDisplayScreen + CustomerAdsPanel + CustomerOrderPanel |
| 3 | Phase 4 | Route in App.jsx + launch button in StartOrderScreen |
| 4 | Phase 5 | Dark theme styling, animations, kiosk UX |
| 5 | Phase 6 | i18n keys (en/ru/uz) |
| 6 | Phase 7 | Manual testing |

**Total: 3 new files + 6 modified files**
