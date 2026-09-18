# Qahvoon Partner API

**For**: delivery aggregators and other external systems that list our venues.
**Base URL**: `https://<host>/api/v1/partner`
**Introduced**: V187

This is the document to hand an integrating partner. It covers the two things an aggregator needs:
pull a venue's menu, and push customer orders back in.

---

## Contents

1. [How it works](#how-it-works)
2. [Authentication](#authentication)
3. [Authorization](#authorization)
4. [Pull the menu](#pull-the-menu)
5. [Push an order](#push-an-order)
6. [Poll an order](#poll-an-order)
7. [Rejections](#rejections)
8. [Rate limits](#rate-limits)
9. [Onboarding checklist](#onboarding-checklist)
10. [Administration (internal)](#administration-internal)

---

## How it works

1. We issue the partner an API key and grant it one or more venues.
2. The partner pulls each granted venue's menu and builds its own catalogue.
3. A customer orders in the partner's app; the partner pushes that order to us.
4. **The kitchen ticket prints at the venue on arrival**, and the order lands on the kitchen
   display, auto-accepted.
5. The partner polls the order for status until it is ready.

There is no "print" endpoint and there will not be one. Printing is a consequence of an order
existing — the push route goes through the same creation path our own channels use, which is what
routes tickets to the right station printers. Asking us to print without creating an order would mean
a paper slip the venue cannot find in any system.

---

## Authentication

Every request carries the key in a header:

```http
X-Partner-Key: elc_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

- The key is issued once, at creation or rotation. We store only a SHA-256 hash, so **we cannot
  recover it** — a lost key must be rotated, which kills the old one everywhere it is installed,
  with no overlap.
- A missing, unknown, revoked or deactivated key is all the same `401`. This is deliberate: it
  denies an attacker a way to tell a real-but-disabled key from a wrong one.
- Keep the key server-side. It is a bearer credential with no expiry and no second factor.

---

## Authorization

A valid key proves **who** is calling, never **what they may touch**. Access is granted per venue,
with two independent capabilities:

| Capability | Default | Grants |
|---|---|---|
| `canReadMenu` | on | `GET /partner/menu/{restaurantId}` |
| `canPushOrders` | **off** | `POST /partner/orders` |

A fresh grant is read-only. Writing into a venue's kitchen is a separate, deliberate decision. A
request for a venue with no active grant — or with the grant but not the capability — is `403`,
with the same message either way so our venue list cannot be enumerated.

---

## Pull the menu

```http
GET /api/v1/partner/menu/{restaurantId}
X-Partner-Key: elc_...
```

```json
{
  "success": true,
  "data": {
    "restaurantId": 3,
    "restaurantName": "Partner Cafe",
    "acceptingOrders": true,
    "deliveryFee": 5000,
    "currency": "UZS",
    "menuVersion": "2026-09-18T10:22:41",
    "categories": [
      {
        "id": 100, "name": "Main", "sortOrder": 0,
        "products": [
          {
            "id": 1, "name": "Osh", "description": "...", "imageUrl": "...",
            "price": 30000, "available": true, "sortOrder": 0,
            "soldByWeight": false,
            "variants": [
              { "id": 11, "name": "Large", "price": 38000, "sku": "OSH-L", "available": true }
            ],
            "addOnGroups": [
              {
                "id": 21, "name": "Extras", "required": false,
                "minSelection": 0, "maxSelection": 3,
                "addOns": [ { "id": 31, "name": "Extra meat", "price": 12000, "available": true } ]
              }
            ]
          }
        ]
      }
    ]
  }
}
```

Three things to build against:

- **`available: false` means sold out, not delisted.** We keep unavailable items in the payload
  rather than dropping them, precisely so a diff against your catalogue does not read "86'd until
  this evening" as "removed" and destroy your own id mapping. Hide them; do not delete them.
- **Variants carry their own price.** When a product has variants, the variant price replaces the
  base `price`. Order a variant by sending its `variantId`.
- **`menuVersion` is the freshest timestamp anywhere in the payload.** Store it; if it has not
  moved, nothing in the menu has, and you can skip rebuilding.

Poll at whatever interval suits you within the rate limit. Prices and availability change during
service, so a cached menu older than a few minutes will start producing rejected orders.

---

## Push an order

```http
POST /api/v1/partner/orders
X-Partner-Key: elc_...
Content-Type: application/json
```

```json
{
  "restaurantId": 3,
  "externalOrderId": "88213",
  "orderType": "DELIVERY",
  "paymentMode": "PREPAID",
  "customer": { "name": "Ali", "phone": "+998901112233" },
  "delivery": {
    "address": "12 Amir Temur, apt 4",
    "latitude": 41.311081,
    "longitude": 69.240562,
    "instructions": "Buzzer is broken, call on arrival"
  },
  "items": [
    {
      "productId": 1,
      "variantId": 11,
      "quantity": 2,
      "addOnIds": [31],
      "specialInstructions": "No onions"
    }
  ],
  "notes": "Leave at reception",
  "expectedTotal": 105000
}
```

| Field | Required | Notes |
|---|---|---|
| `restaurantId` | yes | Must be a venue you hold an order-push grant for. |
| `externalOrderId` | yes | Your own id. Unique per partner, ≤190 chars. This is the dedupe key. |
| `orderType` | yes | `DELIVERY` or `TAKEAWAY`. `DELIVERY` requires `delivery.address`. |
| `paymentMode` | yes | `PREPAID` (you collected) or `CASH` (collected on handover). |
| `items[].productId` | yes | From the menu pull. |
| `items[].variantId` | if the product has variants | Must belong to that product. |
| `items[].addOnIds` | no | Must belong to that product's own add-on groups. |
| `items[].quantity` | yes | 1–100. |
| `customer` | no, but send it | Name and phone reach the venue on the printed ticket. |
| `expectedTotal` | no, but **send it** | See below. |
| `scheduledFor` | no | For pre-orders. |

### `expectedTotal` — send it

`expectedTotal` is the goods plus delivery fee, as you charged the customer. If it disagrees with
our arithmetic by more than 0.01 we **reject the order** (`409 PRICE_MISMATCH`) and return both
numbers.

This exists because the alternative is worse for both of us. Without it, a stale cached price means
the customer pays your number, the venue cooks at ours, and somebody reconciles the difference by
hand — per order, forever. With it, a stale menu fails loudly and immediately, and re-pulling fixes
it. Omit the field and we will accept the order at our price without comment.

### Response — `201 Created`

```json
{
  "success": true,
  "data": {
    "orderId": 500,
    "orderNumber": "ORD-000417",
    "externalOrderId": "88213",
    "status": "ACCEPTED",
    "subtotal": 100000,
    "deliveryFee": 5000,
    "total": 105000,
    "createdAt": "2026-09-18T10:31:02Z",
    "duplicate": false,
    "items": [ { "productId": 1, "productName": "Osh", "variantId": 11, "variantName": "Large",
                 "quantity": 2, "unitPrice": 50000, "totalPrice": 100000 } ]
  }
}
```

`orderNumber` is what the venue and the customer will quote on the phone. Store it.

### Retries are safe

The push is idempotent on `(your partner id, externalOrderId)`. Retry a timed-out request with the
**same** `externalOrderId` and you get the original order back with `"duplicate": true` and status
`200 OK` instead of `201 Created` — nothing is created twice.

Two genuinely simultaneous pushes of the same id will race; the loser gets a `409`. Treat that as
"it landed" and poll rather than retrying again.

Never reuse an `externalOrderId` for a different order. The mapping is permanent and a reused id
returns the old order.

---

## Poll an order

```http
GET /api/v1/partner/orders/{externalOrderId}
X-Partner-Key: elc_...
```

Returns the same object as the push. Look up by **your** id — you never need to store ours, though
`orderNumber` is worth keeping for support.

You can only read orders you created. There are no outbound status webhooks yet; poll while an
order is live.

Statuses you will see: `NEW` → `ACCEPTED` → `PREPARING` → `READY` → `PICKED_UP` / `ON_DELIVERY` →
`DELIVERED` / `COMPLETED`, or `REJECTED` / `CANCELLED`.

---

## Rejections

Errors carry a stable `errors.reason` plus the offending ids. Branch on those, never on the message.

| Status | `reason` | Meaning | What to do |
|---|---|---|---|
| `401` | — | Key missing, unknown, or revoked | Stop. Check the key. Do not retry. |
| `403` | — | No active grant / capability for this venue | Stop. Ask us to grant it. |
| `422` | `UNKNOWN_ITEMS` | Ids not on this venue's menu | **Do not retry.** Re-pull the menu and fix your mapping. |
| `409` | `ITEMS_UNAVAILABLE` | Everything exists, something is sold out | Re-offer the basket without those items. |
| `409` | `PRICE_MISMATCH` | Your total disagrees with ours | Re-pull the menu; re-quote the customer. |
| `409` | `VENUE_NOT_ACCEPTING` | Venue closed or paused | Stop offering the venue; retry later. |
| `429` | `RATE_LIMITED` | Too many requests | Back off. |

```json
{
  "success": false,
  "error": "VALIDATION_ERROR",
  "message": "Some items are not on this venue's menu",
  "errors": {
    "reason": "UNKNOWN_ITEMS",
    "unknownProductIds": [987654],
    "unknownVariantIds": [],
    "unknownAddOnIds": []
  },
  "requestId": "..."
}
```

Every unknown or unavailable id in the whole basket is returned at once, not one per round-trip.

We never substitute an item, drop one silently, or reprice an order. If we cannot make exactly what
you sent at the price you expect, you get a rejection.

---

## Rate limits

600 requests per minute per partner, across all venues and both endpoints. Exceeding it returns
`429`. If you need more for a large venue count, ask — the limit is per partner, not per key.

---

## Onboarding checklist

Before going live, confirm with us:

- [ ] Which venues, and whether each is menu-only or menu + orders.
- [ ] `expectedTotal` is being sent on every push.
- [ ] `externalOrderId` is your permanent order id, not a per-attempt id.
- [ ] Retry logic reuses the same `externalOrderId` and treats `duplicate: true` and `409` as success.
- [ ] `422 UNKNOWN_ITEMS` triggers a menu re-pull, not a retry loop.
- [ ] `available: false` hides an item rather than deleting your mapping.
- [ ] Menu refresh interval agreed (we suggest ≤5 minutes during service).
- [ ] A contact who gets called when orders stop arriving.

---

## Administration (internal)

Staff-side management lives at `/api/v1/partners` (plural) and is **SUPER_ADMIN only** — a partner
spans tenants, so granting it a venue is a platform decision rather than one restaurant's. The admin
UI is at `/admin/partners`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/partners` | List partners and their grants |
| `POST` | `/api/v1/partners` | Create — **returns the raw key once** |
| `POST` | `/api/v1/partners/{id}/rotate-key` | New key; old one dies immediately |
| `PATCH` | `/api/v1/partners/{id}/active?active=` | Kill switch across all venues |
| `PUT` | `/api/v1/partners/{id}/restaurants/{restaurantId}` | Grant / update capabilities |
| `DELETE` | `/api/v1/partners/{id}/restaurants/{restaurantId}` | Revoke one venue |

### Configuration

| Property | Default | Effect |
|---|---|---|
| `app.partner.auto-accept` | `true` | Aggregator orders go straight to `ACCEPTED` and onto the KDS. Set `false` to hold them at `NEW` for staff to confirm, like a website order. |

### Where the order lands

- `orderSource = AGGREGATOR`, visible on the External Orders page and in the order-source filter.
- `customerNotes` leads with `[<Partner name> #<externalOrderId>]` plus the customer's name and
  phone, so the printed ticket answers "whose is this" without anyone opening a screen.
- A `partner_orders` row correlates their id with ours, permanently — it outlives the 24-hour
  idempotency window and is what support reads during a delivery dispute.
- `PREPAID` orders are created with the payment already `COMPLETED` and `paymentGateway` set to the
  partner slug, so the day's takings are not inflated by a debt nobody will collect.
