# Floor map

The live plan of a restaurant's room: what it looks like, and who is sitting in it.

Introduced by migration **V184**. This is not the Tables page — that page manages tables as records
(numbers, capacity, QR codes, merging). This one draws the room and answers "which guest is at table
12, and can I hold table 5 for 8pm?"

---

## What it shows

| Layer | Source | Notes |
|---|---|---|
| Sections | `floor_section` | Drawn polygons — the bar corner, a VIP room. Painted underneath everything. |
| Furniture | `floor_object` | Sofas, chairs, plants, doors, bar fixtures, walls. Belongs to a plan, not a restaurant. |
| Tables | `restaurant_tables` | Real seating with orders and history behind it. Placed on a plan via `floor_plan_id`. |
| Occupancy | live orders | Derived, not stored — see below. |

A restaurant has **several maps** (ground floor, upstairs hall, summer terrace), exactly one of which
is the default. V184 gave every existing restaurant a `Main floor` plan and moved its existing tables
onto it, so the page is never empty on first open.

---

## Occupancy is derived, not stored

`restaurant_tables.status` exists and is still shown, but the map does **not** use it to decide
whether a table is occupied. Occupancy comes from whether the table has an open order:

```
NEW · PLACED · ACCEPTED · PREPARING · READY   → occupied
everything else (COMPLETED, DELIVERED, CANCELLED, REJECTED) → free
```

The stored status is set by hand and drifts. A table left marked `OCCUPIED` after the bill closed is
how a host turns guests away from an empty room; deriving from the order makes that impossible.

Two details worth knowing:

- **One query for the whole room.** A sixty-table restaurant refreshing every few seconds cannot
  afford one query per table, so `FloorPlanService.liveOccupancy` fetches every open order for the
  restaurant once and indexes it by table.
- **Earliest order wins.** A party that orders a second round has two open orders on one table.
  "Seated since" points at when they actually sat down, not at their most recent order.

**Walk-ins** show the order alone with no guest link — they ordered from a waiter and there is no
customer record behind them. Capture happens at payment (see `docs/REGISTRATION_BONUS.md`), not by
making somebody type at seating.

---

## Who can do what

The role gate is **split**, and it is enforced server-side per method — hiding the edit button is
presentation, not security.

| Action | Roles |
|---|---|
| Read the live map | `ADMIN`, `OWNER`, `MANAGER`, `OPERATOR`, `SUPERVISOR`, `HEAD_WAITER`, `WAITER`, `CASHIER` |
| Save layout, add/rename/delete a map | `ADMIN`, `OWNER`, `MANAGER` |

Anyone working a service needs to see the room. Moving furniture mid-service is a decision about the
restaurant, so a stray drag on a waiter's tablet must not be able to rearrange it for everyone.
`RbacGateAnnotationTest.floorMapGateIsSplit` pins both halves — it fails the build if the class gate
narrows below `WAITER` or if any write method widens past `MANAGER`.

---

## API

Base path `/api/v1/floor-plans`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/floor-plans` | Map switcher: names and canvas sizes only. |
| `GET` | `/floor-plans/default` | The default map, everything on it, plus occupancy. |
| `GET` | `/floor-plans/{planId}` | One specific map, same payload. |
| `PUT` | `/floor-plans/{planId}/layout` | Save a whole edit session in one transaction. |
| `POST` | `/floor-plans` | Add a floor, hall or terrace. |
| `PUT` | `/floor-plans/{planId}` | Rename, resize, or make default. |
| `DELETE` | `/floor-plans/{planId}` | Remove a map. |

### Save semantics — read this before changing the editor

`PUT /layout` takes the whole map in one request, deliberately:

- **Tables update in place.** A table omitted from the request is left exactly as it is, never
  deleted. Tables outlive the map — they carry orders, QR codes and history.
- **Objects and sections replace wholesale.** Absent from the request means the editor deleted it,
  because furniture and drawn areas have no life outside the plan.
- **Only geometry is accepted.** A table's number, capacity and status stay with the table endpoints;
  letting the map editor rewrite them would make a stray drag capable of renaming a table.

One request means one transaction means one outcome. A partial write — half the tables moved, the
sections not — is not possible.

### Validation

| Rule | Behaviour |
|---|---|
| Width / height outside 1–5000 | Rejected (`BadRequestException`). A zero-size shape renders as invisible and unclickable. |
| Rotation | **Wraps**, never rejected. 370° means 10°; −90° means 270°. |
| Unknown shape or object type | Rejected, with the valid values named in the message. |
| Section with fewer than 3 points | Rejected. Two points are a line — it draws as nothing and cannot be clicked. |
| Section with no name | Rejected. An unlabelled area cannot be identified on the map. |
| Deleting the last map | Rejected. The page has nothing to draw with zero maps. |

Deleting a map cascades its furniture and sections (V184 foreign keys) but **unplaces** its tables
rather than deleting them — `floor_plan_id` is nulled and they can be placed on another map.

---

## Live updates

Layout and occupancy changes publish to `/topic/restaurant/{restaurantId}/floor`, which
`StompAuthChannelInterceptor` already recognises as tenant-scoped — a session may only subscribe to
its own restaurant's floor. No new topic shape, no new auth rule.

The broadcast is a **signal, not a payload**: it says which restaurant and roughly what happened, and
the client re-fetches the plan it is showing. A restaurant can have several maps open on several
tablets; pushing a whole plan would send three rooms nobody is looking at.

| Event | Published from |
|---|---|
| `floor.occupancy_changed` | `OrderService.updateOrderStatus` (every transition passes through it) and self-service order creation (a QR dine-in order is the moment a party is seated). |
| `floor.layout_changed` | `FloorPlanService.saveLayout`, `createPlan`, `updatePlan`, `deletePlan`. |

Every send is best-effort — a broken WebSocket never fails the order or layout save behind it. The
frontend also re-reads every 30 seconds as a safety net for a dropped socket, and **never** repaints
while an edit is in progress, which would discard unsaved work.

---

## The editor

Reachable at `/restaurants/floor-map`, "Edit layout" (owner/manager only).

- **Corner styles** — `RECTANGLE`, `ROUNDED`, `OVAL`, `SQUARE`. `SQUARE` is genuinely sharp;
  `ROUNDED` softens proportionally to the shorter side so a long bench still reads as a bench; `OVAL`
  is drawn as an ellipse rather than a very round rectangle. Tables and furniture share one picker.
- **Furniture palette** — drop a sofa, chair, plant, door, bar, wall or other object; each starts at a
  sensible size rather than a uniform square.
- **Section tool** — click points on the canvas, then Finish. Polygons are stored as `[{x,y},…]`
  JSONB, not PostGIS: nothing here does spatial queries, so the shape is drawn and hit-tested in the
  browser and never intersected server-side.
- **Snap to grid** — 20 units, toggleable.
- **Rotate** — drag the knob above a selected shape; hold Shift to snap to 15°, which is what you want
  for aligning a row of tables against a wall.
- **Zoom** — buttons, or Ctrl/Cmd/Shift + scroll. Plain scroll still scrolls the page: hijacking it
  traps the user inside the map.

Tables cannot be deleted from the map — that belongs on the Tables page, where the record and its
history live. Removing one from a plan means unplacing it.

---

## Files

| Layer | Path |
|---|---|
| Migration | `src/main/resources/db/migration/V184__floor_plan.sql` |
| Entities | `modules/restaurant/entity/FloorPlan.java`, `FloorObject.java`, `FloorSection.java`, `RestaurantTable.java` |
| Repositories | `modules/restaurant/repository/Floor{Plan,Object,Section}Repository.java` |
| Service | `modules/restaurant/service/FloorPlanService.java`, `FloorEventBroadcaster.java` |
| Controller | `modules/restaurant/controller/FloorPlanController.java` |
| DTOs | `modules/restaurant/dto/FloorPlanView.java`, `FloorLayoutRequest.java` |
| Backend tests | `FloorPlanServiceTest.java`, `RbacGateAnnotationTest.floorMapGateIsSplit` |
| Frontend | `pages/FloorPlan.jsx`, `components/floor/{FloorCanvas,TableDetailsDrawer,QuickReserveDialog}.jsx`, `components/floor/shapes.js` |
| Frontend tests | `pages/FloorPlan.test.jsx`, `components/floor/shapes.test.js` |

### A repository gotcha worth remembering

The three "back-to-front" queries use explicit JPQL rather than derived method names. Spring Data
parses `OrderByZIndexAsc` into the property `ZIndex` — it only lowercases a leading capital when the
*next* character is lowercase, and `ZI` is two capitals — which never matches the `zIndex` attribute
Hibernate registered. The derived form fails at **context startup**, not at query time, so pure-Mockito
unit tests do not catch it; `ApplicationContextSmokeTest` does.
