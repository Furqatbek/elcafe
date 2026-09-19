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
7. [Report a status change](#report-a-status-change)
8. [Rejections](#rejections)
9. [Rate limits](#rate-limits)
10. [Onboarding checklist](#onboarding-checklist)
11. [Administration (internal)](#administration-internal)

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
            "price": 30000, "priceWithMargin": 30000, "available": true, "sortOrder": 0,
            "soldByWeight": false,
            "variants": [
              { "id": 11, "name": "Large", "price": 38000, "priceWithMargin": 38000,
                "sku": "OSH-L", "available": true }
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

**Prices here are channel prices, not our counter prices.** A venue can set a markup for your channel
(commonly to cover your commission), so the same dish may cost more here than in the restaurant. Use
exactly the numbers in this payload — they are the numbers we will charge.

Three things to build against:

- **`available: false` means sold out, not delisted.** We keep unavailable items in the payload
  rather than dropping them, precisely so a diff against your catalogue does not read "86'd until
  this evening" as "removed" and destroy your own id mapping. Hide them; do not delete them.
  An item that disappears from the payload entirely *is* delisted (or the venue was deactivated, in
  which case the whole endpoint returns `404`).

  It is one flag on purpose, and it already combines two things we track separately: a manual "we are
  not serving this today" switch, and whether the kitchen currently has the ingredients. The second
  is derived from stock — when a non-optional ingredient of a dish runs short, the dish goes
  `available: false` on its own, with nobody touching a switch, and comes back when a delivery is
  received. Optional ingredients do not count (a missing garnish is not a sold-out dish), and a
  product with no recipe recorded is always considered makeable. You never need to know which half
  moved.
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

`expectedTotal` is **the goods plus the delivery fee** — the amount you owe the venue for this
ticket. If it disagrees with our arithmetic by more than 0.01 we **reject the order**
(`409 PRICE_MISMATCH`) and return both numbers.

Worth being precise, because the first partner to integrate read it as goods only:

```
expectedTotal  =  Σ (line total at our published prices)  +  deliveryFee
```

Anything you add on top for your own customer — tax, service, tip — sits **outside** this number.
It is deliberately the amount that has to reconcile between the two companies, and nothing else; we
cannot check a figure only one of us can compute. If you send our goods subtotal without the
delivery fee, the rejection says so in a `hint` rather than leaving you hunting a stale menu that
does not exist.

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

You can only read orders you created.

Statuses you will see: `NEW` → `ACCEPTED` → `PREPARING` → `READY` → `PICKED_UP` / `ON_DELIVERY` →
`DELIVERED` / `COMPLETED`, or `REJECTED` / `CANCELLED`.

Polling is the supported way to follow an order today. We also queue a message on every transition
(see [the outbox](#outbound-messages-the-outbox)), but delivering it needs an endpoint on your side
and a dispatcher on ours, neither of which exists yet.

---

## Report a status change

```http
POST /api/v1/partner/orders/{externalOrderId}/status?restaurantId=3
X-Partner-Key: elc_...

{ "status": "ACCEPTED", "reason": null, "occurredAt": "2026-09-19T14:32:00+05:00" }
```

For when the restaurant acts in **your** app rather than ours — the other half of accept and decline
working from either side. Without it your screen shows the order accepted and cooking while our
kitchen display still shows it waiting, and two people end up deciding the same order.

Requires the order-push capability, not menu access: moving an order in someone's kitchen is a write.

**Send your own vocabulary.** We translate:

| You send | Becomes here |
|---|---|
| `ACCEPTED` | `ACCEPTED` |
| `REJECTED` | `REJECTED` |
| `PREPARING` | `PREPARING` |
| `READY` | `READY` |
| `COURIER_ASSIGNED` | `COURIER_ASSIGNED` |
| `PICKED_UP` | `PICKED_UP` |
| `IN_TRANSIT` | `ON_DELIVERY` |
| `DELIVERED` | `DELIVERED` |
| `COMPLETED` | `COMPLETED` |
| `CANCELLED` | `CANCELLED` |
| `CREATED` | nothing — we created it |
| `REFUNDED` | nothing — a fact about money, not about the order |

`CREATED` and `REFUNDED` return `200` and change nothing. That is deliberate rather than a gap:
`REFUNDED` is reachable on your side from `DELIVERED`, and forcing it onto an order state would have
us mark a delivered order cancelled.

**Safe to retry.** Reporting a state the order is already in returns `200` and changes nothing,
rather than failing on our own "you cannot go from `ACCEPTED` to `ACCEPTED`" rule. At-least-once
delivery means the same message will arrive twice eventually, and a retry that errors teaches your
client to stop retrying things it should.

A state the order cannot reach from where it is returns **`409 INVALID_STATUS_TRANSITION`** with both
statuses in `details`. Usually the two sides briefly disagree about where an order has got to, so the
same call may succeed once ours catches up — but being told a delivered order is now preparing is a
bug on one side or the other, and quiet compliance would hide it.

**Cancellation closes at `PREPARING`.** A `CANCELLED` arriving once the kitchen has started is
refused with **`422 CANCELLATION_WINDOW_CLOSED`**, carrying `currentStatus` so you can tell a customer
why. Before that — `PENDING`, `NEW`, `PLACED`, `ACCEPTED` — cancelling is free and always accepted.

The line is where it is because that is where the venue's money goes. Up to `ACCEPTED` nothing has
been cooked and a change of mind costs nobody anything; from `PREPARING` the ingredients are used and
a cook's time is spent, and a full refund means the restaurant has bought a meal nobody eats. It is
the same cutoff our own customers have had all along, so an order behaves the same whichever app it
came from.

`422`, not `409`: an order only moves further forward, so this can never succeed and must not be
retried. What you do for your customer after that is yours to decide — we are telling you the venue
will be cooking and expecting to be paid, not telling you whether to refund.

Staff are not bound by this. A manager cancelling a half-cooked order — a fire, a spoiled delivery —
can still do so from our side, and you will receive the transition as normal.

We do not send a change back to the partner who reported it.

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

### What the partner adds on top

A partner may charge their own customer more than we publish — ZBR adds 8% at their checkout. We
record that per partner (`PATCH /api/v1/partners/{id}/customer-fee`) for exactly one reason: so the
markup editor can show an owner the whole chain.

```
30 000 base  →  34 500 published (venue's +15%)  →  ~37 260 their customer pays (partner's +8%)
```

Without it an owner sets a markup against a price they believe the customer will see, and for any
partner that adds a fee, that belief is wrong by exactly that fee — the lever they think they are
pulling is not the lever they are pulling.

**It is display only, and that is load-bearing.** Nothing prices with it, nothing charges it, no menu
we publish includes it, and a test asserts the published price is identical with the fee set and
unset. If it ever reached the price resolver, every venue would silently charge the aggregator's fee
on top of their own. Zero means none, or that we have not been told; we show no speculative
arithmetic against a partner who has not given us a figure.

The UI says whose number it is. We cannot verify it and do not collect it.

### Channel pricing

A venue's markup for one partner lives on the grant row: `priceAdjustmentType` (`NONE`, `PERCENT`,
`AMOUNT`), `priceAdjustmentValue` and `priceRounding`. Per-category, per-product and per-variant
exceptions live in `partner_price_rules`, resolved most-specific first — variant, product, category,
venue default, base price.

Both the menu endpoint and order pricing resolve through `PartnerPriceResolver`, deliberately the same
object. If they ever price separately, a partner's `expectedTotal` would disagree with ours on every
order and neither side could tell whose arithmetic was wrong; `ChannelPricingIntegrationTest` fails if
anyone splits them.

| Setting | Effect |
|---|---|
| `NONE` (default) | Sell at the base price. Existing grants keep this, so V189 reprices nothing. |
| `PERCENT` | `15` → +15%. Negative is a discount, floored at −100%. |
| `AMOUNT` | `500` → +500. Negative is a discount; the price is floored at zero. |
| `priceRounding` | Round the result to this multiple; `0` disables. `FIXED` overrides skip it. |

Markup applies to products, variants and add-ons. It does **not** apply to the delivery fee, which is
the venue's own charge.

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

### Outbound messages (the outbox)

Anything we send *to* a partner — an order's status changing, later a price or availability update —
is queued in `integration_events` inside the same transaction as the change that caused it, then
delivered by a background worker. A change that rolls back cannot leave a partner notified of it, and
a change that commits cannot lose its notification.

The worker retries with exponential backoff (2s, doubling, capped at 30 minutes), and after
`maxAttempts` moves the message to a dead-letter queue. Dead letters are surfaced on the Partners page
with a one-click requeue, and are never swept by the cleanup job — they are the record of something we
failed to say.

Two behaviours worth knowing:

What produces a message today:

| Event | Subject | When |
|---|---|---|
| `ORDER_STATUS_CHANGED` | `order:<id>` | Any transition on an order a partner pushed — unless that partner is the one who reported it |
| `MENU_ITEM_AVAILABILITY` | `product:<id>` | An ingredient ran short or came back, a manager flipped the switch, or an item was withdrawn from the menu |
| `MENU_ITEM_CHANGED` | `product:<id>` | A product or variant price moved, carrying that partner's channel price under both `price` and `priceWithMargin` |
| `MENU_PRICES_CHANGED` | `menu:<restaurantId>` | That partner's markup for the venue changed, so every price they hold is wrong at once — one message, not one per item |

- **State messages coalesce.** An item flapping across its stock threshold queues one message, not a
  dozen contradictory ones. Order transitions do *not* coalesce: every one is delivered.
- **Availability messages are sent only when the answer changes.** A busy kitchen deducts stock on
  every order; almost none of those deductions cross a threshold, and a message per sale saying
  nothing changed is worse than no message at all.
- **Per-subject ordering.** A failed message holds back later messages about the same order, so a
  partner never sees READY before the ACCEPTED it supersedes.

Delivery is **at-least-once**, not exactly-once: a request that times out after the partner processed
it will be retried. Every message carries a stable id to dedupe on.

Partner-specific protocol lives in a `PartnerEventDispatcher` bean. A partner with no dispatcher never
has messages queued, so the queue never fills with undeliverable work.

| Property | Default | Effect |
|---|---|---|
| `app.partner.outbox.enabled` | `true` | Delivery on/off. Messages keep queuing when off. |
| `app.partner.outbox.poll-ms` | `5000` | How often the worker checks for due messages. |
| `app.partner.outbox.batch-size` | `50` | Messages per pass. |

### What we know about ZBR, and what they still owe us

From their reply of 2026-09-19. Recorded here because their side of this contract is the input to
ours, and half of it is "not built yet" rather than "documented elsewhere".

**Their state model**, which is what [the status endpoint](#report-a-status-change) translates:

```
CREATED → ACCEPTED → PREPARING → READY → COURIER_ASSIGNED
       → PICKED_UP → IN_TRANSIT → DELIVERED → COMPLETED
```

`CANCELLED` is reachable from everything up to `COURIER_ASSIGNED`; `REFUNDED` is separate and
reachable from `CANCELLED`, `DELIVERED` and `COMPLETED`. Two transitions are not the naive linear
path: a courier is often assigned while food is still cooking, and `READY → PICKED_UP` is the common
pickup path. They auto-cancel unpaid orders after 30 minutes, and a decline after payment refunds
automatically — **best-effort**, by their own account: a failed provider call is logged for manual
settlement while the order still reads `CANCELLED`.

**Their order reference** is `FD-YYYYMMDD-XXXXXX`, assigned at creation and never changed. That is
the value to key on when we start calling them.

**Their importer** reads `priceWithMargin` exactly as sent and applies a margin of its own when only
`price` is present — which is why we now send both keys carrying the same channel price. It has **no
deletion path**: an item we delete stays live and orderable on their side indefinitely. Withdrawing
an item here therefore goes out as `available: false` rather than as silence, which is the closest
thing to a delete their catalogue can act on.

**Their timeouts** are 5s connect and 10s read; they suggest we assume the same of them. Their
proposed partner limit is 60 requests/minute with burst to 120, `429` with `Retry-After`.

**What they asked us for, and the answer:**

| Their ask | Answer |
|---|---|
| An order-creation endpoint that prints and routes like a till order | `POST /partner/orders`. Yes — it goes through the same creation path, which is what routes tickets to station printers. There is no "print" endpoint by design. |
| A write-scoped credential per venue | The partner key plus a per-venue grant, with `canPushOrders` off by default. |
| Our idempotency semantics | `externalOrderId` in the body, unique per `(partner, venue, id)`. A replay returns the original order with `duplicate: true` and `200` instead of `201`. |
| What happens to an order naming a product we no longer have | Refused, `422 UNKNOWN_ITEMS`, listing every unknown id at once. We will not accept a line we cannot cook or price. Free-text lines are not supported and we would rather not add them — an order the kitchen cannot read is worse than a refused one. |
| An endpoint for order-status webhooks | Built: [`POST /partner/orders/{externalOrderId}/status`](#report-a-status-change). Every state in their model is accepted; `CREATED` and `REFUNDED` are acknowledged without effect. |

### Calling ZBR

Their partner API landed, and `ZbrEventDispatcher` speaks it. Everything ZBR-shaped is in that one
class; the outbox still knows nothing about HTTP.

| Our event | Their call |
|---|---|
| `MENU_ITEM_AVAILABILITY` | `PATCH /api/v1/partner/venues/{ourVenueId}/menu/items/{ourProductId}` with `available` only |
| `MENU_ITEM_CHANGED` | the same `PATCH`, with `price` and `available` |
| `MENU_PRICES_CHANGED` | `POST .../menu/items` — every live item at its new channel price, in batches of 200 |
| `ORDER_STATUS_CHANGED` | `POST /api/v1/partner/orders/{theirReference}/status` |

**Whose id names what** looks inconsistent until you see the rule: menus are ours, so their menu
endpoints take our venue and product ids; orders were created on their side and pushed to us, so
their order endpoint takes their `FD-...` reference — which we already hold as `externalOrderId`,
because it is what they sent when they pushed the order. Whoever owns the thing names it.

**Their order vocabulary is four words** — `ACCEPTED`, `PREPARING`, `READY`, `DECLINED` — against
our thirteen. Both `REJECTED` and `CANCELLED` become `DECLINED`, which refunds their customer
automatically. Everything from `PICKED_UP` onwards maps to nothing and is not sent: their API does
not know the word, and retrying it to a dead letter would turn an ordinary order into an operator
alert.

**The decline reason reaches their customer.** Up to 500 characters, trimmed if longer. Worth
knowing before typing one: "we have run out of lamb" is a different conversation from silence, and
so is an internal note.

**A `MENU_PRICES_CHANGED` becomes the whole menu.** Their API cannot read our menu back, so "your
prices moved, re-read them" has nowhere to land — we push what the new ones are, priced through the
same resolver that prices the menu endpoint and the order push, so the three cannot disagree.

**What is retried and what is not.** A timeout, a 5xx or a `429` goes back to the outbox to back off
and retry. A `403`, `404`, `409` or `422` is logged and dropped: an item they have never heard of
will not start existing because we ask again, and burning ten attempts to reach that conclusion
fills the dead-letter queue with noise.

**One asymmetry worth knowing:** a venue may decline on their side at any point, including mid-cook
— their words. Our cancellation cutoff binds their *customer*, not the venue, so the two rules do
not collide.

### Still open with them

1. **Their keys and venue grants.** They issue; we receive. Start on staging (`staging.zbrr.uz`
   with a key stamped `staging`), then ask for a production credential — it is separate, and the
   staging one will not work there. Both go in `APP_PARTNER_ZBR_BASE_URL` / `APP_PARTNER_ZBR_API_KEY`.
2. **They still have no way to push us an order over an API** — by their own note, pushing orders to
   us is still theirs to build. Today they push to `POST /partner/orders` here, which works; the
   note is about their side of the round trip.
3. **Whether the price their customer sees is exactly the price we send.** Their contract now says
   `price` is charged to the customer exactly as sent and they add nothing, which is the answer we
   wanted. Worth one line of confirmation that it is policy and not just current behaviour.

**Answered since:** they asked where the cancellation cutoff should sit for our venues. **At
`PREPARING`**, and our side now enforces it: a partner-reported cancellation after the kitchen has
started is refused. That matches the cutoff our own customers have always had, so the answer costs
them no special case — it is the rule the rest of our product already runs on.

### Known gaps

- **No ZBR dispatcher yet.** The outbox, retries and dead-lettering are built and tested; the adapter
  that actually calls a partner's API needs their contract. Until one exists for a partner, nothing is
  queued for them.
- **Delivery needs a dispatcher per partner.** ZBR's exists (`ZbrEventDispatcher`); any other
  partner queues nothing until theirs does.
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
