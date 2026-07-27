# Customer Profile (Customer 360)

One guest, everything known about them: who they are, what they like and must avoid, what they have
spent, where they stand on loyalty, and every message exchanged across every channel.

Added in **V183**. Reached from the customer list (click the `#id`), and gated to `ADMIN` / `OWNER` /
`MANAGER` — this screen concentrates a person's phone, birthday, spend and entire message history in one
place, which is more than a waiter taking an order needs.

---

## What it is made of

Nothing on this page is stored twice. It is assembled on read from the systems that already own each
fact, so it cannot drift out of date with them:

| Section | Comes from |
|---|---|
| Identity, notes, tags | `customers` |
| Preferences | `customer_preference` (new in V183) |
| Purchases | `orders` + `order_items` |
| Loyalty standing | `customer_loyalty`, `customer_tiers` |
| Conversations | `instagram_inbound_message`, `instagram_logs`, `telegram_logs`, `sms_logs` |

## Preferences

Likes, dislikes, allergies and dietary needs, one row each.

This information could already be typed into `Customer.notes`, and that is exactly the problem: a
sentence is fine to read and useless to act on. You cannot ask a paragraph "which of my guests are
vegetarian", and a waiter cannot be warned about an allergy the system never modelled. A table can
answer both, which is why preferences are structured rather than a JSON blob or free text.

- **`ALLERGY` is deliberately not a kind of `DISLIKE`.** They read the same in a list and mean very
  different things — one is taste, the other is safety — so the UI colours allergies differently.
  Because the type is stored as a string, the default ordering is alphabetical on the enum name, which
  puts allergies at the top of the list. That is the order you want, and a test pins it — switching the
  column to an ordinal would silently reshuffle it.
- **`source` is `MANUAL` or `DERIVED`.** Everything recorded through the API is `MANUAL`; the request
  DTO has no `source` field at all, so a caller cannot pass off a guess as something the guest actually
  said. `DERIVED` is reserved for future inference.
- **The same fact cannot be recorded twice** (`UNIQUE (customer_id, preference_type, preference_value)`) — a
  duplicate on an allergy list reads as disagreement.
- **Most-ordered items are computed, never stored** as `DERIVED` rows. A habit that changes should
  change with it rather than lingering as a stale "like".

`GET|POST /api/v1/customers/{id}/preferences`, `DELETE .../preferences/{preferenceId}`.

## Purchases

Computed from the guest's orders, counting only `COMPLETED`, `DELIVERED` and `PICKED_UP` — **a
cancelled order is not a purchase** and must never inflate spend or the average. Gives order count,
lifetime spend, average order value, first and last order, and the five most-ordered items.

Reads a bounded window of recent orders (500) rather than the whole history, because this runs on every
profile open. The cap is far above any real customer; if it were ever exceeded the failure mode is
understating a very old first order, not a wrong balance.

## Conversations

`GET /api/v1/customers/{id}/timeline?before=<ISO timestamp>&limit=20`

Every channel merged into one stream, newest first. The underlying tables agree on almost nothing —
they key on a subscriber, a phone or a customer, and store `OffsetDateTime` in one place and
`LocalDateTime` in another — so each row is normalised to a common shape with one comparable instant
before merging. Without that, channels interleave in the wrong order.

**Paging is cursored on timestamp, not an offset.** The stream is merged from tables that are still
being written to, so an offset page would skip or repeat messages the moment a new one arrived
mid-scroll. Feed `nextCursor` back as `before` to load the next older window; `hasMore` tells the UI
whether to show "load older messages".

> **Known asymmetry, not a bug.** Only Instagram stores what the guest *sent* us
> (`instagram_inbound_message`, V179). Telegram and SMS keep send logs only, so their side of the
> conversation is everything we said and nothing they replied. The `direction` field makes that visible
> rather than letting a one-sided thread look complete.

## Privacy

- **Tenant-scoped**: another restaurant's customer id reads as not-found, never as forbidden.
- **Erasure**: preferences are destroyed with the customer by
  `CustomerProfileService.onCustomerDeleted`, an explicit `CustomerDeletedEvent` listener that runs
  synchronously inside the customer's own delete transaction — the same shape the Telegram and
  Instagram modules use for their PII.

  This used to rest on V183's `ON DELETE CASCADE` alone, which was wrong in a way worth remembering:
  the JPA mapping never expressed the cascade — `CustomerPreference.customer` is a plain
  `@ManyToOne` — so on any schema Hibernate generates from the entities the foreign key has no cascade
  at all. Deleting a customer there did not quietly orphan preferences, it **failed outright** with a
  referential-integrity violation. A guarantee the object model contradicts is a property of one
  deployment, not a guarantee. The migration's cascade remains as a backstop for deletes that bypass
  the service.

  `CustomerPreferenceRepositoryTest.preferencesMustBeErasedBeforeTheCustomerRow` pins the constraint
  itself, so nobody removes the listener as "redundant, the migration cascades".

## Source

- Service: `modules/customer/service/CustomerProfileService.java`
- Controller: `modules/customer/controller/CustomerProfileController.java`
- Entity / repository: `modules/customer/entity/CustomerPreference.java`,
  `repository/CustomerPreferenceRepository.java`
- DTOs: `modules/customer/dto/CustomerProfileResponse.java`, `CustomerTimelineEntry.java`,
  `CustomerTimelineResponse.java`, `CustomerPreferenceRequest.java`, `CustomerPreferenceResponse.java`
- Migration: `db/migration/V183__customer_preferences.sql`
- Frontend: `frontend/src/pages/CustomerProfile.jsx`, linked from `pages/Customers.jsx`
- Related: `LOYALTY_SYSTEM.md` (balances, tiers, the welcome bonus), `INSTAGRAM_SETUP.md` (the inbox
  this timeline borrows its transcript shape from)
