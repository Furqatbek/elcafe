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
4. **The kitchen ticket prints at the venue on arrival** — always, regardless of settings — and,
   unless the venue has turned auto-accept off to review orders first, the order also lands on the
   kitchen display straight away.
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
| `canPushOrders` | **off** | `POST /partner/orders` **and** `GET /partner/orders/{id}` — losing it also stops you reading back orders you already pushed |

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
  An item that disappears from the payload entirely *is* delisted (or the venue was deactivated, in
  which case the whole endpoint returns `404`).
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
| `orderType` | yes | Send `DELIVERY` or `TAKEAWAY`. `DELIVERY` requires `delivery.address`. (`DINE_IN` is accepted by the schema but meaningless from an aggregator — it is not rejected, so do not send it.) |
| `paymentMode` | yes | `PREPAID` (you collected) or `CASH` (collected on handover). |
| `items[].productId` | yes | From the menu pull. |
| `items[].variantId` | **required** if the product has variants | Must belong to that product. Omitting it is refused (`422 VARIANT_REQUIRED`) rather than charged at the base price. |
| `items[].addOnIds` | no | Must belong to that product's own add-on groups. |
| `items[].quantity` | yes | 1–100. |
| `items` | yes | 1–200 lines. Exceeding it is a plain `400` validation error, not a branded rejection. |
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

The push is idempotent on `(your partner id, restaurantId, externalOrderId)`. Retry a timed-out
request with the **same** `externalOrderId` and the same `restaurantId` and you get the original
order back with `"duplicate": true` and status `200 OK` instead of `201 Created` — nothing is created
twice.

The key includes the venue, so if you number orders per store you do not need to make them globally
unique: the same `"1001"` at two venues is two orders.

Two genuinely simultaneous pushes of the same id also resolve to `200` with `"duplicate": true` —
the loser of the race is answered with the winner's order rather than an error, and the order exists
once.

Never reuse an `externalOrderId` for a different order at the same venue. The mapping is permanent
and a reused id returns the old order.

---

## Poll an order

```http
GET /api/v1/partner/orders/{externalOrderId}?restaurantId=3
X-Partner-Key: elc_...
```

Returns the same object as the push. Look up by **your** id — you never need to store ours, though
`orderNumber` is worth keeping for support. `restaurantId` is required because your order ids are
only unique within a venue, and the venue grant is re-checked on every read: if we revoke a venue,
its orders stop being readable too.

You can only read orders you created. There are no outbound status webhooks yet; poll while an
order is live.

Statuses you will see: `NEW` → `ACCEPTED` → `PREPARING` → `READY` → `PICKED_UP` / `ON_DELIVERY` →
`DELIVERED` / `COMPLETED`, or `REJECTED` / `CANCELLED`.

---

## Rejections

Errors carry a stable `errors.reason` plus the offending ids. Branch on those, never on the message.

| Status | `reason` | Meaning | Did the order land? | What to do |
|---|---|---|---|---|
| `401` | — | Key missing, unknown, or revoked | No | Stop. Check the key. Do not retry. |
| `403` | — | No active grant / capability for this venue | No | Stop. Ask us to grant it. |
| `422` | `UNKNOWN_ITEMS` | Ids not on this venue's menu | No | **Do not retry.** Re-pull the menu and fix your mapping. |
| `422` | `VARIANT_REQUIRED` | A product sold by size/variant was sent without a `variantId` | No | **Do not retry.** Send the variant. |
| `409` | `ITEMS_UNAVAILABLE` | Everything exists, something is sold out | No | Re-offer the basket without those items. |
| `409` | `PRICE_MISMATCH` | Your total disagrees with ours | No | Re-pull the menu; re-quote the customer. |
| `409` | `VENUE_NOT_ACCEPTING` | Venue closed or paused | No | Stop offering the venue; retry later. |
| `429` | *(none)* | Too many requests | No | Back off; honour `Retry-After`. This one has no `reason` — branch on the top-level `error: "RATE_LIMITED"`. |

**A `409` does not mean the order landed.** Four of the five above mean nothing was created and the
customer is not getting food. The one exception is the concurrent-duplicate race described under
[Retries are safe](#retries-are-safe), which returns `200` with `"duplicate": true` rather than a
`409`. Branch on `errors.reason`, not on the status code alone.

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
- [ ] Retry logic reuses the same `externalOrderId` **and** `restaurantId`, and treats only
      `duplicate: true` as "already landed" — **never a bare `409`**, which means the order was
      refused and the customer is not getting food.
- [ ] `422` (`UNKNOWN_ITEMS` / `VARIANT_REQUIRED`) triggers a menu re-pull, not a retry loop.
- [ ] Products with variants always send a `variantId`.
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

### Known gaps

- **No outbound status webhooks.** Partners poll. Pushing status out needs outbound HTTP with retry,
  a dead-letter path and SSRF guarding, none of which exists in this codebase yet.
- **Subscription suspension does not reach partner traffic.** `SubscriptionEnforcementFilter` gates
  staff and waiter principals only, so a suspended tenant's venues keep serving menus and accepting
  aggregator orders while their own staff are locked out of the POS. Whether that is wrong depends on
  what suspension is meant to mean commercially — it is called out here so the decision is made
  deliberately rather than discovered.
- **A simultaneous double push may print twice, but only with `app.printing.use-agent=false`.**
  Under the default queue the print job is a `print_jobs` row written inside the same transaction as
  the order, so the losing request's rollback takes the row with it and nothing reaches the agent.
  With direct socket/USB printing there is no transaction to roll back and the paper is already out.
- **Print-on-arrival requires a configured printer.** If a venue has neither an active kitchen station
  with a printer nor an enabled `KITCHEN` printer, printing logs a warning and does nothing. The order
  is still created; it just never becomes paper.
- **An order held at NEW raises no in-app notification.** With auto-accept off, the ticket prints but
  the staff dashboard gets no WebSocket event until someone accepts it.
