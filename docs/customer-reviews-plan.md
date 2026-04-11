# Customer Reviews & Feedback — Implementation Plan

## Overview

Customers can rate their order (1-5 stars) and leave optional comments after order completion. Reviews are linked to orders, customers, and restaurants. The system auto-prompts for reviews via the order status page and notification channels.

## Architecture

```
Order Completed
  ├─► OrderStatusPage shows star rating widget
  ├─► SMS/Telegram/Push sends review request link
  └─► Customer submits review
        ├─► Review stored in database
        ├─► Restaurant average rating recalculated
        ├─► Owner notified via Telegram (low ratings)
        └─► Analytics dashboard updated
```

---

## Phase 1: Database & Entities

### Migration: `V113__create_reviews_table.sql`

```sql
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    customer_id BIGINT REFERENCES customers(id),
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    food_rating INTEGER CHECK (food_rating >= 1 AND food_rating <= 5),
    service_rating INTEGER CHECK (service_rating >= 1 AND service_rating <= 5),
    comment TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    reply TEXT,
    replied_at TIMESTAMP,
    replied_by VARCHAR(100),
    is_verified BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_reviews_order ON reviews(order_id);
CREATE INDEX idx_reviews_restaurant ON reviews(restaurant_id);
CREATE INDEX idx_reviews_customer ON reviews(customer_id);
CREATE INDEX idx_reviews_rating ON reviews(restaurant_id, rating);
CREATE INDEX idx_reviews_created ON reviews(restaurant_id, created_at DESC);
```

### Entity: `Review.java`

Fields:
- `id`, `order` (OneToOne), `customer` (ManyToOne), `restaurant` (ManyToOne)
- `rating` (1-5, overall), `foodRating` (1-5, optional), `serviceRating` (1-5, optional)
- `comment` (text, optional)
- `status` enum: `PUBLISHED`, `HIDDEN`, `FLAGGED`
- `reply` (owner's response), `repliedAt`, `repliedBy`
- `isVerified` (order actually exists and was completed)
- `createdAt`, `updatedAt`

### Existing entity to modify:

**`Restaurant.java`** — `rating` field already exists. Add:
```java
@Column(name = "review_count")
@Builder.Default
private Integer reviewCount = 0;
```

Migration: `V114__add_review_count_to_restaurants.sql`

---

## Phase 2: Repository

### `ReviewRepository.java`

- `findByOrderId(Long orderId)` — check if already reviewed
- `findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId)` — list reviews
- `findByCustomerId(Long customerId)` — customer's review history
- `@Query` average rating by restaurant
- `@Query` rating distribution (count per star)
- `@Query` average by date range for analytics
- `countByRestaurantId(Long restaurantId)`

---

## Phase 3: DTOs

1. **`SubmitReviewRequest.java`** — orderId, rating, foodRating, serviceRating, comment
2. **`ReviewResponse.java`** — all review fields + customerName + orderNumber
3. **`ReviewSummaryDTO.java`** — averageRating, totalReviews, ratingDistribution (1-5 counts), recentReviews
4. **`ReplyToReviewRequest.java`** — reply text

---

## Phase 4: Service Layer

### `ReviewService.java`

| Method | Description |
|--------|-------------|
| `submitReview(SubmitReviewRequest)` | Validate order exists + completed + not already reviewed, save review, update restaurant rating |
| `getReviewByOrderId(Long orderId)` | Check if review exists for order |
| `getRestaurantReviews(Long restaurantId, Pageable)` | Paginated reviews |
| `getReviewSummary(Long restaurantId)` | Average rating + distribution + recent |
| `replyToReview(Long reviewId, ReplyToReviewRequest)` | Owner replies to review |
| `hideReview(Long reviewId)` | Moderate — hide inappropriate review |
| `flagReview(Long reviewId)` | Flag for manual review |
| `getCustomerReviews(Long customerId)` | Customer's review history |
| `recalculateRestaurantRating(Long restaurantId)` | AVG(rating) → Restaurant.rating |

Key logic in `submitReview()`:
```
1. Validate order exists and status is COMPLETED/DELIVERED/READY
2. Check no existing review for this order (UNIQUE constraint)
3. Create Review entity (isVerified=true since order exists)
4. Save review
5. Recalculate restaurant average rating
6. If rating <= 2: notify owner via Telegram (low rating alert)
```

---

## Phase 5: Controller Layer

### `PublicReviewController.java`

**Base path:** `/api/v1/public/reviews`
**Security:** Public — no auth required (customers submit from order status page)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/` | Submit review (validates order number or token) |
| GET | `/restaurant/{restaurantId}` | List published reviews (paginated) |
| GET | `/restaurant/{restaurantId}/summary` | Rating summary (avg + distribution) |
| GET | `/order/{orderNumber}` | Check if order has been reviewed |

### `ReviewController.java`

**Base path:** `/api/v1/reviews`
**Security:** `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/restaurant/{restaurantId}` | All reviews (including hidden) |
| POST | `/{id}/reply` | Reply to review |
| POST | `/{id}/hide` | Hide review |
| POST | `/{id}/flag` | Flag review |
| GET | `/restaurant/{restaurantId}/analytics` | Review analytics |

---

## Phase 6: Frontend — Customer Side

### 6a. Review Widget in OrderStatusPage.jsx

After order status shows COMPLETED/READY, render a review card:

```
┌──────────────────────────────────┐
│  How was your experience?        │
│                                  │
│  ★ ★ ★ ★ ★  (tap to rate)      │
│                                  │
│  Food:    ★ ★ ★ ★ ★  (optional) │
│  Service: ★ ★ ★ ★ ★  (optional) │
│                                  │
│  [Leave a comment...]            │
│                                  │
│  [Submit Review]                 │
│                                  │
│  ✅ Thank you for your feedback! │
└──────────────────────────────────┘
```

### 6b. Public Reviews Page

Route: `/restaurant/{id}/reviews` (customer-facing, shows published reviews)

---

## Phase 7: Frontend — Admin Side

### 7a. Reviews Management Page

Route: `/admin/reviews`

```
┌─────────────────────────────────────────────────┐
│ Customer Reviews          Restaurant: [dropdown] │
├─────────────────────────────────────────────────┤
│ ┌──────────┬──────────┬──────────┬────────────┐ │
│ │ Avg: 4.2 │ Total:89 │ 5★: 45% │ This week: │ │
│ │ ★★★★☆   │ reviews  │ 4★: 30% │ +12 reviews│ │
│ └──────────┴──────────┴──────────┴────────────┘ │
├─────────────────────────────────────────────────┤
│ Filter: [All | 5★ | 4★ | 3★ | 2★ | 1★]        │
├─────────────────────────────────────────────────┤
│ ★★★★★  "Amazing food!"  — John D.  ORD-042     │
│         [Reply] [Hide]                           │
│                                                  │
│ ★★☆☆☆  "Service was slow"  — Jane S.  ORD-038  │
│         Owner reply: "Sorry, we'll improve!"     │
│         [Edit Reply] [Hide]                      │
└─────────────────────────────────────────────────┘
```

### 7b. Modify Existing Pages

- **Dashboard.jsx** — add average rating + recent reviews widget
- **FinancialAnalytics** or **CustomerAnalytics** — add review trends chart

---

## Phase 8: Notification Integration

After order reaches COMPLETED/DELIVERED status, auto-send review request:

### Trigger Point

In `OrderFlowService` or via Spring Event listener:
```java
@EventListener
public void onOrderCompleted(OrderCompletedEvent event) {
    // Wait 30 minutes then send review request
    reviewNotificationService.scheduleReviewRequest(event.getOrder());
}
```

### Channels

1. **SMS**: "Thank you for dining at {restaurant}! Rate your experience: {link}"
2. **Telegram**: Same message with inline buttons (1-5 stars)
3. **Push**: In-app notification with deep link to review
4. **Owner Telegram Bot**: Alert on low ratings (1-2 stars) for immediate action

### Review Link

`/order/{orderNumber}/review` — public page with the star rating widget

---

## Phase 9: i18n

Translation keys for all three locales (en/ru/uz):

```json
{
  "review.title": "Customer Reviews",
  "review.howWasExperience": "How was your experience?",
  "review.tapToRate": "Tap to rate",
  "review.foodRating": "Food Quality",
  "review.serviceRating": "Service",
  "review.comment": "Leave a comment (optional)",
  "review.commentPlaceholder": "Tell us about your experience...",
  "review.submit": "Submit Review",
  "review.thankYou": "Thank you for your feedback!",
  "review.alreadyReviewed": "You already reviewed this order",
  "review.summary": "Review Summary",
  "review.averageRating": "Average Rating",
  "review.totalReviews": "Total Reviews",
  "review.recentReviews": "Recent Reviews",
  "review.reply": "Reply",
  "review.ownerReply": "Owner's Reply",
  "review.hide": "Hide Review",
  "review.flag": "Flag Review",
  "review.noReviews": "No reviews yet",
  "review.ratingDistribution": "Rating Distribution",
  "review.stars": "stars",
  "review.verified": "Verified Order"
}
```

---

## Phase 10: Testing

### Unit Tests (~15)
- `ReviewServiceTest` — submit, duplicate prevention, rating recalculation, owner notification on low rating

### Controller Tests (~10)
- `PublicReviewControllerTest` — submit, list, summary, already reviewed check
- `ReviewControllerTest` — reply, hide, flag

### Integration Tests (~5)
- `ReviewRepositoryTest` — average calculation, distribution, pagination

---

## Implementation Order

| Step | Phase | Files | Description |
|------|-------|-------|-------------|
| 1 | Phase 1 | 2 migrations + 1 entity + modify Restaurant | Database + entity |
| 2 | Phase 2 | 1 repository | Data access |
| 3 | Phase 3 | 4 DTOs | Request/response objects |
| 4 | Phase 4 | 1 service | Core business logic |
| 5 | Phase 5 | 2 controllers | Public + admin REST APIs |
| 6 | Phase 6 | 2 frontend pages/components | Customer review widget + public page |
| 7 | Phase 7 | 1 new page + 1 modified | Admin reviews management |
| 8 | Phase 8 | 1 notification service | Auto-send review requests |
| 9 | Phase 9 | 3 i18n files | Translations |
| 10 | Phase 10 | 3 test files (~30 tests) | Unit + integration tests |

**Total: ~12 new files + ~5 modified files + ~30 tests**
