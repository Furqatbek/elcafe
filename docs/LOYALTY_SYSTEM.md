# Loyalty & Bonus Points System

**Version:** 1.2
**Last Updated:** 2026-06-08
**Author:** ElCafe Development Team

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Database Schema](#database-schema)
4. [Configuration](#configuration)
5. [Customer Tiers](#customer-tiers)
6. [Bonus Accrual](#bonus-accrual)
7. [Bonus Redemption](#bonus-redemption)
8. [Refund Handling](#refund-handling)
9. [Retention Mechanics](#retention-mechanics)
10. [API Integration](#api-integration)
11. [Event-Driven Processing](#event-driven-processing)
12. [Audit Trail](#audit-trail)
13. [Usage Examples](#usage-examples)
14. [Troubleshooting](#troubleshooting)
15. [Loyalty Milestones (Stamp Card Rewards)](#loyalty-milestones-stamp-card-rewards)
16. [Customer QR Identity & POS Attach](#customer-qr-identity--pos-attach)
17. [Customer Wallet Top-Ups](#customer-wallet-top-ups)

---

## Overview

The ElCafe Loyalty & Bonus Points System is a comprehensive customer retention solution that rewards customers with bonus points for purchases and provides tier-based benefits. The system is designed to:

- **Increase Customer Retention**: Reward repeat customers with bonus points
- **Drive Higher Spend**: Tier-based multipliers encourage customers to spend more
- **Reduce Churn**: Birthday bonuses and reactivation campaigns bring customers back
- **Maintain Data Integrity**: Full audit trail with idempotent transactions
- **Ensure Backward Compatibility**: Seamlessly integrated with existing order flow

### Key Features

✅ **Automatic Bonus Accrual** - 5% of order total credited as bonus points
✅ **Tier-Based Multipliers** - Up to 2x bonus for VIP customers
✅ **Flexible Redemption** - Use bonuses for up to 50% of order payment
✅ **Retention Mechanics** - Birthday, first order, and reactivation bonuses
✅ **Proportional Refunds** - Accurate bonus rollback on order cancellations
✅ **Promotion Support** - Time-limited bonus campaigns
✅ **Complete Audit Trail** - Every transaction logged with metadata
✅ **Idempotent Operations** - Prevents duplicate bonus credits

---

## System Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Order Flow                           │
│  Customer places order → Order completed → Event fired  │
└─────────────────┬───────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────┐
│          LoyaltyOrderEventListener                      │
│  @TransactionalEventListener(AFTER_COMMIT)              │
└─────────────────┬───────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────┐
│              LoyaltyService                             │
│  • processOrderCompletion()                             │
│  • calculateBonus()                                     │
│  • applyTierMultiplier()                                │
│  • applyPromotions()                                    │
└─────────────┬───────────────────────────────────────────┘
              │
              ├──────────────┬──────────────┬──────────────┐
              ▼              ▼              ▼              ▼
      ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
      │  Bonus   │   │   Tier   │   │Customer  │   │ Loyalty  │
      │ Service  │   │ Service  │   │ Loyalty  │   │Promotion │
      └──────────┘   └──────────┘   └──────────┘   └──────────┘
```

### Technology Stack

- **Backend Framework**: Spring Boot 3.x
- **Database**: PostgreSQL 14+
- **ORM**: Spring Data JPA / Hibernate
- **Migration**: Flyway
- **Event Processing**: Spring Events (@TransactionalEventListener)
- **Async Processing**: @Async with thread pool
- **JSON Storage**: PostgreSQL JSONB with custom converter

---

## Database Schema

### Entity Relationship Diagram

```
┌─────────────────┐
│  Customer       │
└────────┬────────┘
         │ 1
         │
         │ 1
┌────────▼────────────┐       ┌──────────────────┐
│ CustomerLoyalty     │──────▶│ CustomerTier     │
│                     │   N:1 │                  │
│ - currentBalance    │       │ - name           │
│ - lifetimeEarned    │       │ - bonusMultiplier│
│ - totalSpent        │       │ - minTotalSpend  │
│ - orderCount        │       └──────────────────┘
└──────┬──────────────┘
       │ 1
       │
       │ N
┌──────▼──────────────┐       ┌──────────────────┐
│ BonusTransaction    │──────▶│ Order            │
│                     │   N:1 │                  │
│ - transactionType   │       └──────────────────┘
│ - amount            │
│ - balanceAfter      │
│ - idempotencyKey    │
│ - metadata (JSONB)  │
└─────────────────────┘

┌─────────────────────┐       ┌──────────────────┐
│ TierHistory         │──────▶│ CustomerLoyalty  │
│                     │   N:1 │                  │
│ - previousTier      │       └──────────────────┘
│ - newTier           │
│ - reason            │
└─────────────────────┘

┌─────────────────────┐
│ LoyaltyConfig       │
│                     │
│ - bonusPercentage   │
│ - maxBonusUsage     │
│ - firstOrderBonus   │
│ - birthdayBonus     │
│ - pointsExpireDays  │
└─────────────────────┘

┌─────────────────────┐       ┌──────────────────┐
│ LoyaltyPromotion    │──────▶│ Restaurant       │
│                     │   N:1 │                  │
│ - name              │       └──────────────────┘
│ - bonusMultiplier   │
│ - startDate         │
│ - endDate           │
└─────────────────────┘
```

### Table Descriptions

#### `customer_tiers`
Defines VIP tier levels with their benefits.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| name | VARCHAR(50) | Tier name (New, Regular, Gold, VIP) |
| level | INTEGER | Tier hierarchy (1-4) |
| min_total_spend | DECIMAL(10,2) | Minimum lifetime spend |
| min_order_count | INTEGER | Minimum order count |
| bonus_multiplier | DECIMAL(3,2) | Bonus point multiplier (1.0-2.0) |
| benefits | JSONB | Additional tier benefits |

**Default Tiers:**
- **New** (Level 1): 0 spend, 1.0x multiplier
- **Regular** (Level 2): $500 spend, 1.2x multiplier
- **Gold** (Level 3): $2000 spend, 1.5x multiplier
- **VIP** (Level 4): $5000 spend, 2.0x multiplier

#### `loyalty_config`
Global loyalty system configuration.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| restaurant_id | BIGINT | FK to restaurants (NULL = global) |
| bonus_percentage | DECIMAL(5,2) | Base % of order as bonus (default 5%) |
| max_bonus_usage_percent | DECIMAL(5,2) | Max % of order payable with bonus (50%) |
| first_order_bonus | DECIMAL(10,2) | New customer bonus (300) |
| birthday_bonus | DECIMAL(10,2) | Birthday bonus (500) |
| reactivation_bonus | DECIMAL(10,2) | Inactive customer bonus (200) |
| reactivation_days | INTEGER | Days inactive before eligible (90) |
| points_expire_days | INTEGER | Bonus expiration (365, NULL = never) |
| min_order_for_bonus | DECIMAL(10,2) | Min order to earn bonus |

#### `customer_loyalty`
Tracks individual customer loyalty accounts.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| customer_id | BIGINT | FK to customers (UNIQUE) |
| tier_id | BIGINT | FK to customer_tiers |
| current_balance | DECIMAL(10,2) | Available bonus points |
| lifetime_earned | DECIMAL(10,2) | Total earned all-time |
| lifetime_spent | DECIMAL(10,2) | Total used all-time |
| total_spent | DECIMAL(10,2) | Total order spend (for tier calc) |
| order_count | INTEGER | Total order count |
| last_order_date | TIMESTAMP | Most recent order |
| first_order_bonus_claimed | BOOLEAN | First order bonus flag |
| birthday_bonus_claimed_year | INTEGER | Last year birthday bonus claimed |

#### `bonus_transactions`
Ledger-style audit trail of all bonus movements.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| customer_loyalty_id | BIGINT | FK to customer_loyalty |
| transaction_type | VARCHAR(30) | EARNED, SPENT, REFUNDED, etc. |
| amount | DECIMAL(10,2) | Transaction amount (positive or negative) |
| balance_after | DECIMAL(10,2) | Balance snapshot after transaction |
| order_id | BIGINT | FK to orders (if applicable) |
| description | TEXT | Human-readable description |
| idempotency_key | VARCHAR(255) | Unique key for deduplication |
| metadata | JSONB | Additional context |
| created_at | TIMESTAMP | Transaction timestamp |

**Transaction Types:**
- `EARNED`: Bonus earned from order
- `SPENT`: Bonus used for payment
- `REFUNDED`: Bonus refunded due to order cancellation
- `EXPIRED`: Bonus expired
- `ADJUSTMENT`: Manual adjustment
- `BIRTHDAY_BONUS`: Birthday bonus
- `FIRST_ORDER_BONUS`: First order bonus
- `REACTIVATION_BONUS`: Reactivation bonus
- `PROMOTION_BONUS`: Promotional bonus
- `ADMIN_ADJUSTMENT`: Admin manual adjustment

#### `tier_history`
Records of customer tier changes.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| customer_loyalty_id | BIGINT | FK to customer_loyalty |
| previous_tier_id | BIGINT | FK to customer_tiers |
| new_tier_id | BIGINT | FK to customer_tiers |
| reason | VARCHAR(100) | MILESTONE_REACHED, MANUAL_ADJUSTMENT |
| changed_at | TIMESTAMP | When tier changed |

#### `loyalty_promotions`
Time-limited promotional campaigns.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| restaurant_id | BIGINT | FK to restaurants (NULL = all) |
| name | VARCHAR(100) | Promotion name |
| description | TEXT | Promotion details |
| bonus_multiplier | DECIMAL(3,2) | Additional multiplier (e.g., 1.5 = +50%) |
| start_date | TIMESTAMP | Promotion start |
| end_date | TIMESTAMP | Promotion end |
| is_active | BOOLEAN | Active flag |
| min_order_amount | DECIMAL(10,2) | Minimum order to qualify |

---

## Configuration

### Global Configuration

The system uses a default global configuration that applies to all restaurants. Configuration is stored in the `loyalty_config` table.

**Default Settings:**
```json
{
  "bonusPercentage": 5.0,
  "maxBonusUsagePercent": 50.0,
  "firstOrderBonus": 300.0,
  "birthdayBonus": 500.0,
  "reactivationBonus": 200.0,
  "reactivationDays": 90,
  "pointsExpireDays": 365,
  "minOrderForBonus": 0.0
}
```

### Restaurant-Specific Configuration

Each restaurant can override the global configuration by creating a restaurant-specific config:

```sql
INSERT INTO loyalty_config (
  restaurant_id, bonus_percentage, max_bonus_usage_percent
) VALUES (
  1, 7.0, 60.0
);
```

### Application Properties

Configure async processing in `application.yml`:

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 5
        max-size: 10
        queue-capacity: 100
```

---

## Customer Tiers

### Tier Structure

The system includes 4 default tiers with automatic upgrades:

| Tier | Min Spend | Min Orders | Multiplier | Benefits |
|------|-----------|------------|------------|----------|
| **New** | $0 | 0 | 1.0x | Base bonus rate |
| **Regular** | $500 | 5 | 1.2x | +20% bonus points |
| **Gold** | $2,000 | 20 | 1.5x | +50% bonus points |
| **VIP** | $5,000 | 50 | 2.0x | +100% bonus points (double) |

### Automatic Tier Upgrades

Tier upgrades happen automatically after each order:

```java
// Example: Customer spends $2,100 total with 22 orders
// Current tier: Regular (1.2x)
// Eligible for: Gold (requires $2000 and 20 orders)
// System automatically upgrades to Gold tier
```

**Upgrade Logic:**
1. After each order completion, system recalculates customer's total spend and order count
2. Checks highest tier customer qualifies for
3. If higher tier available, upgrades customer
4. Records upgrade in `tier_history` table
5. Next order immediately benefits from new multiplier

### Tier Benefits

Beyond bonus multipliers, tiers can include additional benefits stored in the `benefits` JSONB field:

```json
{
  "priority_support": true,
  "free_delivery": true,
  "exclusive_menu_items": ["truffle-special", "caviar-appetizer"],
  "reservation_priority": 1,
  "complimentary_drinks": 2
}
```

---

## Bonus Accrual

### Calculation Flow

```
Order Total ($100)
    ↓
Base Bonus = $100 × 5% = $5
    ↓
Tier Multiplier (Gold 1.5x) = $5 × 1.5 = $7.50
    ↓
Promotion Multiplier (Happy Hour 2x) = $7.50 × 2 = $15
    ↓
Final Bonus Credited = $15
```

### Code Example

```java
public void processOrderCompletion(Order order) {
    // 1. Get or create customer loyalty account
    CustomerLoyalty loyalty = getOrCreateLoyalty(order.getCustomerId());

    // 2. Get configuration
    LoyaltyConfig config = getConfig(order.getRestaurantId());

    // 3. Calculate base bonus (5% of order total)
    BigDecimal baseBonus = order.getTotal()
        .multiply(config.getBonusPercentage())
        .divide(BigDecimal.valueOf(100));

    // 4. Apply tier multiplier (e.g., 1.5x for Gold)
    BigDecimal tierMultiplier = loyalty.getTier().getBonusMultiplier();
    BigDecimal bonusWithTier = baseBonus.multiply(tierMultiplier);

    // 5. Apply promotions (e.g., 2x during happy hour)
    BigDecimal finalBonus = applyPromotions(bonusWithTier, order, config);

    // 6. Record transaction (idempotent)
    String idempotencyKey = "order-bonus-" + order.getId();
    bonusService.recordTransaction(
        loyalty,
        TransactionType.EARNED,
        finalBonus,
        order,
        "Bonus earned from order #" + order.getId(),
        idempotencyKey,
        Map.of("orderTotal", order.getTotal(), "tierMultiplier", tierMultiplier)
    );

    // 7. Check for tier upgrade
    tierService.checkAndUpgradeTier(loyalty);

    // 8. Check for special bonuses
    if (!loyalty.isFirstOrderBonusClaimed()) {
        grantFirstOrderBonus(loyalty);
    }
}
```

### Idempotency

All bonus transactions use an idempotency key to prevent duplicate credits:

```java
String idempotencyKey = "order-bonus-" + order.getId();

// Check if transaction already exists
if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
    log.info("Transaction already processed: {}", idempotencyKey);
    return;
}

// Safe to create transaction
bonusService.recordTransaction(..., idempotencyKey, ...);
```

**Key Formats:**
- Order bonus: `order-bonus-{orderId}`
- First order: `first-order-{customerId}`
- Birthday: `birthday-{customerId}-{year}`
- Reactivation: `reactivation-{customerId}-{timestamp}`

---

## Bonus Redemption

### Usage Limits

Customers can use bonuses to pay for orders, subject to limits:

- **Maximum Usage**: 50% of order total (configurable)
- **Minimum Balance**: Must have sufficient balance
- **Real-time Validation**: Checked before payment processing

### Redemption Flow

```java
public void useBonusForPayment(Long customerId, Long orderId, BigDecimal bonusToUse) {
    // 1. Get loyalty account
    CustomerLoyalty loyalty = getLoyalty(customerId);

    // 2. Get order and config
    Order order = orderRepository.findById(orderId);
    LoyaltyConfig config = getConfig(order.getRestaurantId());

    // 3. Validate bonus amount
    BigDecimal maxAllowed = order.getTotal()
        .multiply(config.getMaxBonusUsagePercent())
        .divide(BigDecimal.valueOf(100));

    if (bonusToUse.compareTo(maxAllowed) > 0) {
        throw new IllegalArgumentException(
            "Cannot use more than " + config.getMaxBonusUsagePercent() + "% of order"
        );
    }

    // 4. Check sufficient balance
    if (!loyalty.hasSufficientBalance(bonusToUse)) {
        throw new InsufficientBonusException(
            "Insufficient bonus balance. Available: " + loyalty.getCurrentBalance()
        );
    }

    // 5. Deduct bonus
    loyalty.deductBonus(bonusToUse);

    // 6. Update order
    order.setBonusUsed(bonusToUse);
    orderRepository.save(order);

    // 7. Record transaction
    bonusService.recordTransaction(
        loyalty,
        TransactionType.SPENT,
        bonusToUse.negate(), // Negative amount
        order,
        "Bonus used for order payment",
        "payment-" + orderId,
        Map.of("orderTotal", order.getTotal())
    );
}
```

### Frontend Integration Example

```javascript
// Calculate maximum bonus allowed
const maxBonusAllowed = orderTotal * 0.50; // 50%
const availableBonus = customerLoyalty.currentBalance;
const maxUsable = Math.min(maxBonusAllowed, availableBonus);

// Show slider or input
<Input
  type="number"
  max={maxUsable}
  value={bonusToUse}
  onChange={(e) => setBonusToUse(e.target.value)}
  label="Use Bonus Points (max 50% of order)"
/>

// Submit payment with bonus
const response = await fetch('/api/v1/payments/process', {
  method: 'POST',
  body: JSON.stringify({
    orderId: order.id,
    paymentMethod: 'CARD',
    bonusToUse: bonusToUse,
    cashAmount: orderTotal - bonusToUse
  })
});
```

---

## Refund Handling

### Proportional Refund Logic

When an order is refunded, bonuses are rolled back proportionally:

**Scenario:**
- Order Total: $100
- Bonus Earned: $5
- Bonus Used for Payment: $20
- Refund Amount: $60 (60% of order)

**Refund Calculation:**
```
Earned Bonus to Refund = $5 × 60% = $3
Used Bonus to Refund = $20 × 60% = $12

Net Bonus Change = -$3 + $12 = +$9
```

### Code Implementation

```java
public void processRefund(Order order, BigDecimal refundAmount) {
    CustomerLoyalty loyalty = getLoyalty(order.getCustomerId());

    // Calculate refund percentage
    BigDecimal refundPercentage = refundAmount
        .divide(order.getTotal(), 4, RoundingMode.HALF_UP);

    // 1. Reverse earned bonus (proportional)
    BigDecimal earnedBonusToReverse = findEarnedBonus(order)
        .multiply(refundPercentage);

    if (earnedBonusToReverse.compareTo(BigDecimal.ZERO) > 0) {
        loyalty.deductBonus(earnedBonusToReverse);
        bonusService.recordTransaction(
            loyalty,
            TransactionType.REFUNDED,
            earnedBonusToReverse.negate(),
            order,
            "Refund: earned bonus reversed",
            "refund-earned-" + order.getId(),
            Map.of("refundAmount", refundAmount, "refundPercentage", refundPercentage)
        );
    }

    // 2. Return used bonus (proportional)
    BigDecimal usedBonusToReturn = order.getBonusUsed()
        .multiply(refundPercentage);

    if (usedBonusToReturn.compareTo(BigDecimal.ZERO) > 0) {
        loyalty.addBonus(usedBonusToReturn);
        bonusService.recordTransaction(
            loyalty,
            TransactionType.REFUNDED,
            usedBonusToReturn,
            order,
            "Refund: used bonus returned",
            "refund-used-" + order.getId(),
            Map.of("refundAmount", refundAmount)
        );
    }

    // 3. Update customer spend totals
    loyalty.setTotalSpent(
        loyalty.getTotalSpent().subtract(refundAmount)
    );

    // 4. Check for tier downgrade
    tierService.checkAndDowngradeTier(loyalty);
}
```

---

## Retention Mechanics

### First Order Bonus

**Amount**: $3.00 (300 points)
**Trigger**: Automatically on first completed order
**Idempotency**: Customer can only receive once

```java
private void grantFirstOrderBonus(CustomerLoyalty loyalty) {
    if (loyalty.isFirstOrderBonusClaimed()) {
        return;
    }

    LoyaltyConfig config = getConfig(null); // Global config

    bonusService.recordTransaction(
        loyalty,
        TransactionType.FIRST_ORDER_BONUS,
        config.getFirstOrderBonus(),
        null,
        "Welcome bonus for first order!",
        "first-order-" + loyalty.getCustomerId(),
        Map.of("programVersion", "1.0")
    );

    loyalty.setFirstOrderBonusClaimed(true);
}
```

### Birthday Bonus

**Amount**: $5.00 (500 points)
**Trigger**: Manual grant via API endpoint
**Frequency**: Once per year
**Note**: Automatic detection disabled (Customer entity missing birthdate field)

```java
// API Endpoint
POST /api/v1/loyalty/customers/{id}/birthday-bonus

// Implementation
public void grantBirthdayBonus(Long customerId) {
    CustomerLoyalty loyalty = getLoyalty(customerId);
    LoyaltyConfig config = getConfig(null);
    int currentYear = LocalDate.now().getYear();

    // Check if already claimed this year
    if (loyalty.getBirthdayBonusClaimedYear() != null &&
        loyalty.getBirthdayBonusClaimedYear() == currentYear) {
        throw new BonusAlreadyClaimedException(
            "Birthday bonus already claimed for " + currentYear
        );
    }

    bonusService.recordTransaction(
        loyalty,
        TransactionType.BIRTHDAY_BONUS,
        config.getBirthdayBonus(),
        null,
        "Happy Birthday! Bonus from ElCafe",
        "birthday-" + customerId + "-" + currentYear,
        Map.of("year", currentYear)
    );

    loyalty.setBirthdayBonusClaimedYear(currentYear);
}
```

**Manual Process:**
1. Export customer birthdays from database
2. Filter customers with birthdays today/this week
3. Call API endpoint for each customer
4. System prevents duplicate claims via year tracking

### Reactivation Bonus

**Amount**: $2.00 (200 points)
**Trigger**: Manual grant via API or automated job
**Eligibility**: No orders in past 90 days (configurable)

```java
// Find inactive customers
@Query("SELECT cl FROM CustomerLoyalty cl WHERE " +
       "cl.lastOrderDate IS NOT NULL AND cl.lastOrderDate < :thresholdDate")
List<CustomerLoyalty> findInactiveCustomers(@Param("thresholdDate") LocalDateTime thresholdDate);

// Grant reactivation bonus
public void grantReactivationBonus(Long customerId) {
    CustomerLoyalty loyalty = getLoyalty(customerId);
    LoyaltyConfig config = getConfig(null);

    // Check if inactive
    LocalDateTime threshold = LocalDateTime.now()
        .minusDays(config.getReactivationDays());

    if (loyalty.getLastOrderDate() == null ||
        loyalty.getLastOrderDate().isAfter(threshold)) {
        throw new NotEligibleException(
            "Customer not inactive long enough for reactivation bonus"
        );
    }

    bonusService.recordTransaction(
        loyalty,
        TransactionType.REACTIVATION_BONUS,
        config.getReactivationBonus(),
        null,
        "Welcome back! We missed you",
        "reactivation-" + customerId + "-" + System.currentTimeMillis(),
        Map.of("lastOrderDate", loyalty.getLastOrderDate())
    );
}
```

**Automated Campaign:**
```java
@Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
public void runReactivationCampaign() {
    LoyaltyConfig config = getConfig(null);
    LocalDateTime threshold = LocalDateTime.now()
        .minusDays(config.getReactivationDays());

    List<CustomerLoyalty> inactiveCustomers =
        loyaltyRepository.findInactiveCustomers(threshold);

    for (CustomerLoyalty loyalty : inactiveCustomers) {
        try {
            grantReactivationBonus(loyalty.getCustomerId());
            // Send email/SMS notification
            notificationService.sendReactivationEmail(loyalty.getCustomer());
        } catch (Exception e) {
            log.error("Failed to grant reactivation bonus", e);
        }
    }
}
```

---

## API Integration

### REST Endpoints

#### Get Customer Loyalty Info

```http
GET /api/v1/loyalty/customers/{customerId}
```

**Response:**
```json
{
  "id": 1,
  "customerId": 42,
  "currentBalance": 1250.50,
  "lifetimeEarned": 3500.00,
  "lifetimeSpent": 2249.50,
  "totalSpent": 15000.00,
  "orderCount": 47,
  "tier": {
    "id": 3,
    "name": "Gold",
    "level": 3,
    "bonusMultiplier": 1.50
  },
  "lastOrderDate": "2025-12-10T14:30:00",
  "firstOrderBonusClaimed": true,
  "birthdayBonusClaimedYear": 2025
}
```

#### Get Transaction History

```http
GET /api/v1/loyalty/customers/{customerId}/transactions?page=0&size=20
```

**Response:**
```json
{
  "content": [
    {
      "id": 156,
      "transactionType": "EARNED",
      "amount": 15.00,
      "balanceAfter": 1250.50,
      "description": "Bonus earned from order #3421",
      "createdAt": "2025-12-15T10:30:00",
      "metadata": {
        "orderTotal": 100.00,
        "tierMultiplier": 1.5,
        "promotionApplied": false
      }
    },
    {
      "id": 155,
      "transactionType": "SPENT",
      "amount": -50.00,
      "balanceAfter": 1235.50,
      "description": "Bonus used for order payment",
      "createdAt": "2025-12-14T18:45:00"
    }
  ],
  "totalElements": 89,
  "totalPages": 5,
  "size": 20,
  "number": 0
}
```

#### Grant Birthday Bonus

```http
POST /api/v1/loyalty/customers/{customerId}/birthday-bonus
```

**Response:**
```json
{
  "success": true,
  "message": "Birthday bonus of 500.00 granted successfully",
  "newBalance": 1750.50,
  "transactionId": 157
}
```

#### Grant Reactivation Bonus

```http
POST /api/v1/loyalty/customers/{customerId}/reactivation-bonus
```

**Response:**
```json
{
  "success": true,
  "message": "Reactivation bonus of 200.00 granted successfully",
  "newBalance": 1950.50,
  "transactionId": 158
}
```

### Service Integration

#### Publishing Order Events

To trigger automatic bonus processing, publish events after order completion:

```java
@Service
public class OrderService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public void completeOrder(Long orderId) {
        Order order = orderRepository.findById(orderId);

        // Update order status
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        // Publish event for loyalty system
        eventPublisher.publishEvent(
            new OrderCompletedEvent(order)
        );
    }
}
```

#### Using Bonus for Payment

Integrate bonus redemption in payment flow:

```java
@Service
public class PaymentService {

    @Autowired
    private LoyaltyService loyaltyService;

    public PaymentResult processPayment(PaymentRequest request) {
        Order order = orderRepository.findById(request.getOrderId());

        BigDecimal totalDue = order.getTotal();
        BigDecimal bonusUsed = BigDecimal.ZERO;

        // Use bonus if requested
        if (request.getBonusToUse() != null &&
            request.getBonusToUse().compareTo(BigDecimal.ZERO) > 0) {

            loyaltyService.useBonusForPayment(
                order.getCustomerId(),
                order.getId(),
                request.getBonusToUse()
            );

            bonusUsed = request.getBonusToUse();
            totalDue = totalDue.subtract(bonusUsed);
        }

        // Process remaining payment via card/cash
        PaymentResult result = paymentGateway.charge(totalDue);

        return result;
    }
}
```

---

## Event-Driven Processing

### Event Listeners

The system uses Spring's `@TransactionalEventListener` to ensure bonus processing happens **after** the order transaction commits:

```java
@Component
public class LoyaltyOrderEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            log.info("Processing loyalty for order {}", event.getOrder().getId());
            loyaltyService.processOrderCompletion(event.getOrder());
        } catch (Exception e) {
            log.error("Error processing loyalty", e);
            // Don't throw - loyalty processing should not fail the order
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleOrderRefunded(OrderRefundedEvent event) {
        try {
            loyaltyService.processRefund(
                event.getOrder(),
                event.getRefundAmount()
            );
        } catch (Exception e) {
            log.error("Error processing refund", e);
        }
    }
}
```

### Why AFTER_COMMIT?

- **Data Consistency**: Ensures order is fully persisted before bonus processing
- **Prevents Premature Credits**: If order transaction rolls back, no bonus is credited
- **Audit Trail**: Bonus transactions reference valid order IDs

### Async Processing

Marked with `@Async` to:
- Prevent blocking order completion
- Improve response times
- Allow parallel processing of multiple orders

**Configuration:**
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 5
        max-size: 10
```

---

## Audit Trail

### Complete Transaction History

Every bonus movement is logged as an immutable transaction record:

```sql
SELECT
    bt.id,
    bt.transaction_type,
    bt.amount,
    bt.balance_after,
    bt.description,
    bt.created_at,
    o.id AS order_id,
    o.total AS order_total
FROM bonus_transactions bt
LEFT JOIN orders o ON bt.order_id = o.id
WHERE bt.customer_loyalty_id = 1
ORDER BY bt.created_at DESC;
```

### Metadata Storage

Additional context stored in JSONB `metadata` field:

```json
{
  "orderTotal": 150.00,
  "tierMultiplier": 1.5,
  "promotionApplied": true,
  "promotionName": "Happy Hour 2x",
  "bonusBeforePromotion": 7.50,
  "bonusAfterPromotion": 15.00,
  "programVersion": "1.0"
}
```

### Reconciliation Queries

Verify balance integrity:

```sql
-- Recalculate balance from transactions
SELECT
    cl.id,
    cl.current_balance AS recorded_balance,
    COALESCE(SUM(bt.amount), 0) AS calculated_balance,
    cl.current_balance - COALESCE(SUM(bt.amount), 0) AS discrepancy
FROM customer_loyalty cl
LEFT JOIN bonus_transactions bt ON bt.customer_loyalty_id = cl.id
GROUP BY cl.id, cl.current_balance
HAVING cl.current_balance != COALESCE(SUM(bt.amount), 0);
```

---

## Usage Examples

### Example 1: New Customer Journey

```
Day 1 - First Order
-------------------
Order: $50.00
Base Bonus: $50 × 5% = $2.50
Tier: New (1.0x) = $2.50 × 1.0 = $2.50
First Order Bonus: +$3.00
Total Earned: $5.50
Balance: $5.50

Day 7 - Second Order
--------------------
Order: $75.00
Base Bonus: $75 × 5% = $3.75
Tier: New (1.0x) = $3.75
Total Earned: $3.75
Balance: $9.25

Day 30 - Tier Upgrade
---------------------
Total Spend: $525 (10 orders)
Upgrade: New → Regular (1.2x multiplier)

Day 31 - First Order as Regular
-------------------------------
Order: $100.00
Base Bonus: $100 × 5% = $5.00
Tier: Regular (1.2x) = $5.00 × 1.2 = $6.00
Total Earned: $6.00
Balance: $50.25
```

### Example 2: Using Bonus for Payment

```
Customer Balance: $120.00
Order Total: $80.00
Max Bonus Usage: 50% = $40.00

Payment Breakdown:
- Bonus Used: $40.00
- Cash/Card: $40.00
- Total: $80.00

New Bonus Earned: $80 × 5% × 1.5 (Gold) = $6.00
New Balance: $120 - $40 + $6 = $86.00
```

### Example 3: Partial Refund

```
Original Order:
- Total: $100.00
- Bonus Earned: $7.50 (Gold 1.5x)
- Bonus Used: $30.00

Refund: $60.00 (60% of order)

Adjustments:
- Earned Bonus Reversed: $7.50 × 60% = -$4.50
- Used Bonus Returned: $30 × 60% = +$18.00
- Net Change: +$13.50

Balance Before: $200.00
Balance After: $213.50
```

---

## Troubleshooting

### Issue: Bonus Not Credited After Order

**Symptoms:**
- Order completed successfully
- No bonus transaction created

**Debugging:**
```java
// Check event listener logs
log.info("Handling order completed event for order {}", event.getOrder().getId());

// Verify customer loyalty account exists
CustomerLoyalty loyalty = loyaltyRepository.findByCustomerId(customerId);
if (loyalty == null) {
    log.error("No loyalty account found for customer {}", customerId);
}

// Check for idempotency key collision
boolean exists = transactionRepository.existsByIdempotencyKey("order-bonus-" + orderId);
if (exists) {
    log.info("Transaction already processed, skipping");
}
```

**Solutions:**
1. Ensure `@EnableAsync` is present in configuration
2. Verify event is published: `eventPublisher.publishEvent(new OrderCompletedEvent(order))`
3. Check database constraints (e.g., foreign keys)
4. Review application logs for exceptions

### Issue: Duplicate Bonus Credits

**Symptoms:**
- Customer received bonus twice for same order
- `bonus_transactions` has duplicate entries

**Root Cause:**
- Idempotency key not unique
- Transaction retried without checking existing record

**Solution:**
```java
// Always check before creating
if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
    return; // Already processed
}

// Use unique idempotency keys
String key = String.format("order-bonus-%d", order.getId());
```

### Issue: Balance Discrepancy

**Symptoms:**
- `customer_loyalty.current_balance` doesn't match sum of transactions

**Diagnosis Query:**
```sql
SELECT
    cl.id,
    cl.current_balance,
    COALESCE(SUM(bt.amount), 0) AS calculated,
    cl.current_balance - COALESCE(SUM(bt.amount), 0) AS diff
FROM customer_loyalty cl
LEFT JOIN bonus_transactions bt ON bt.customer_loyalty_id = cl.id
WHERE cl.id = 123
GROUP BY cl.id;
```

**Solution:**
```java
// Recalculate from transactions
BigDecimal recalculated = transactionRepository
    .findByCustomerLoyaltyId(loyaltyId)
    .stream()
    .map(BonusTransaction::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

loyalty.setCurrentBalance(recalculated);
loyaltyRepository.save(loyalty);
```

### Issue: Birthday Bonus Not Available

**Symptoms:**
- Birthday bonus API returns error
- Cannot grant birthday bonus

**Root Cause:**
- Customer entity missing `birthdate` field
- Automatic birthday detection disabled

**Workaround:**
```java
// Manual process:
// 1. Export birthdays from external CRM
// 2. Call API manually for each customer

POST /api/v1/loyalty/customers/42/birthday-bonus

// System will validate:
// - Not already claimed this year
// - Customer loyalty account exists
```

**Long-term Solution:**
Add `birthdate` field to Customer entity and enable automatic query.

### Issue: Tier Not Upgrading

**Symptoms:**
- Customer meets spend threshold
- Tier remains unchanged

**Debugging:**
```java
// Check tier upgrade logic
public void checkAndUpgradeTier(CustomerLoyalty loyalty) {
    List<CustomerTier> allTiers = tierRepository.findAllByOrderByLevelDesc();

    for (CustomerTier tier : allTiers) {
        boolean meetsSpend = loyalty.getTotalSpent()
            .compareTo(tier.getMinTotalSpend()) >= 0;
        boolean meetsOrders = loyalty.getOrderCount() >= tier.getMinOrderCount();

        log.info("Checking tier {}: spend={}, orders={}",
            tier.getName(), meetsSpend, meetsOrders);

        if (meetsSpend && meetsOrders &&
            tier.getLevel() > loyalty.getTier().getLevel()) {
            // Upgrade
            upgradeTier(loyalty, tier);
            return;
        }
    }
}
```

**Solutions:**
1. Verify `customer_tiers` table has correct thresholds
2. Ensure `total_spent` and `order_count` are updating correctly
3. Check tier upgrade is called after order processing

---

## Best Practices

### 1. Always Use Idempotency Keys

```java
// GOOD
String key = "order-bonus-" + order.getId();
if (!transactionRepository.existsByIdempotencyKey(key)) {
    recordTransaction(..., key, ...);
}

// BAD
recordTransaction(..., null, ...); // Risk of duplicates
```

### 2. Handle Events Gracefully

```java
// GOOD
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void handleOrderCompleted(OrderCompletedEvent event) {
    try {
        loyaltyService.processOrderCompletion(event.getOrder());
    } catch (Exception e) {
        log.error("Loyalty processing failed", e);
        // Don't throw - order should succeed even if bonus fails
    }
}

// BAD
public void handleOrderCompleted(OrderCompletedEvent event) {
    loyaltyService.processOrderCompletion(event.getOrder());
    // Exception will fail the entire order transaction
}
```

### 3. Validate Before Deductions

```java
// GOOD
if (!loyalty.hasSufficientBalance(bonusToUse)) {
    throw new InsufficientBonusException();
}
loyalty.deductBonus(bonusToUse);

// BAD
loyalty.deductBonus(bonusToUse); // Could go negative
```

### 4. Store Rich Metadata

```java
// GOOD
Map<String, Object> metadata = Map.of(
    "orderTotal", order.getTotal(),
    "tierMultiplier", tierMultiplier,
    "promotionApplied", promotionName != null,
    "restaurantId", order.getRestaurantId()
);

// BAD
Map<String, Object> metadata = Map.of(); // No context for debugging
```

### 5. Test Edge Cases

- Zero-value orders
- Refunds exceeding original order
- Concurrent bonus usage
- Tier downgrades (if implemented)
- Expired bonuses

---

## Future Enhancements

### Planned Features

1. **Automatic Birthday Detection**
   - Add `birthdate` to Customer entity
   - Enable scheduled job to grant bonuses automatically
   - Send birthday emails/SMS

2. **Bonus Expiration**
   - Implement scheduled job to expire old bonuses
   - Warn customers before expiration
   - Track expiration in transaction history

3. **Referral Program**
   - Grant bonus when customer refers friend
   - Track referral codes
   - Reward both referrer and referee

4. **Spend-Based Challenges**
   - "Spend $500 this month, get 1000 bonus points"
   - Progress tracking dashboard
   - Achievement badges

5. **Partner Integration**
   - Earn bonuses at partner restaurants
   - Unified loyalty across restaurant group
   - Cross-brand redemption

---

## Loyalty Milestones (Stamp Card Rewards)

### Overview

The milestone system provides stamp-card style rewards (e.g., "Visit 10 times, get 1 free item"). Restaurants can configure multiple milestones with different reward types and visit requirements.

### Key Features

- **Configurable Visit Thresholds**: Set how many visits are needed (e.g., 5, 10, 20)
- **Multiple Reward Types**: FREE_ITEM, DISCOUNT_PERCENTAGE, DISCOUNT_FIXED, BONUS_POINTS
- **Minimum Order Amount**: Only count visits where the order total meets a threshold
- **Repeating Milestones**: Optionally reset after completion for ongoing rewards
- **Automatic Visit Tracking**: Visits are recorded automatically on order completion via event listener

### Database Schema

#### `loyalty_milestones`

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| restaurant_id | BIGINT | Restaurant FK |
| name | VARCHAR(255) | Milestone name |
| description | TEXT | Description |
| required_visits | INTEGER | Visits needed to earn reward |
| reward_type | VARCHAR(30) | FREE_ITEM, DISCOUNT_PERCENTAGE, DISCOUNT_FIXED, BONUS_POINTS |
| reward_value | DECIMAL(10,2) | Discount amount or bonus points |
| reward_product_id | BIGINT | Product FK (for FREE_ITEM rewards) |
| min_order_amount | DECIMAL(10,2) | Minimum order total to count as a visit |
| is_repeating | BOOLEAN | Whether milestone resets after completion |
| active | BOOLEAN | Whether milestone is active |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |

#### `milestone_redemptions`

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | Primary key |
| milestone_id | BIGINT | Milestone FK |
| customer_id | BIGINT | Customer FK |
| current_visits | INTEGER | Current visit count toward milestone |
| total_completions | INTEGER | Number of times milestone was completed |
| reward_pending | BOOLEAN | Whether a reward is waiting to be redeemed |
| last_visit_order_id | BIGINT | Last qualifying order FK |
| last_completion_at | TIMESTAMP | When milestone was last completed |
| last_redemption_at | TIMESTAMP | When reward was last redeemed |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |

### API Endpoints

#### Admin/Manager Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/restaurants/{restaurantId}/milestones` | Create a milestone |
| PUT | `/api/v1/milestones/{milestoneId}` | Update a milestone |
| GET | `/api/v1/milestones/{milestoneId}` | Get milestone by ID |
| GET | `/api/v1/restaurants/{restaurantId}/milestones` | Get all restaurant milestones |
| DELETE | `/api/v1/milestones/{milestoneId}` | Delete a milestone |

#### Customer Progress Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/restaurants/{restaurantId}/milestones/customers/{customerId}/progress` | Get customer's milestone progress |
| GET | `/api/v1/milestones/customers/{customerId}/pending-rewards` | Get pending rewards |
| POST | `/api/v1/milestones/{milestoneId}/customers/{customerId}/redeem` | Redeem a pending reward |

### How It Works

1. **Admin creates a milestone** for a restaurant (e.g., "Coffee Lover Card" — 10 visits for a free coffee)
2. **Customer places an order** — the `LoyaltyOrderEventListener` fires after order completion
3. **MilestoneService.processOrderCompletion()** checks all active milestones:
   - Skips if order total < milestone's `minOrderAmount`
   - Skips non-repeating milestones already completed
   - Skips milestones with pending (unredeemed) rewards
   - Increments visit counter and checks if threshold reached
4. **When threshold reached**: `rewardPending` is set to `true`, `totalCompletions` incremented
5. **Staff redeems reward** via the redeem endpoint, which resets the counter if repeating

### Example: Create a "Buy 10, Get 1 Free" Milestone

```json
POST /api/v1/restaurants/1/milestones
{
  "name": "Coffee Lover Card",
  "description": "Buy 10 coffees and get 1 free!",
  "requiredVisits": 10,
  "rewardType": "FREE_ITEM",
  "rewardProductId": 42,
  "minOrderAmount": 15000,
  "isRepeating": true
}
```

### Example: Create a Discount Milestone

```json
POST /api/v1/restaurants/1/milestones
{
  "name": "Loyal Customer Reward",
  "description": "Visit 5 times and get 20% off your next order",
  "requiredVisits": 5,
  "rewardType": "DISCOUNT_PERCENTAGE",
  "rewardValue": 20.00,
  "minOrderAmount": 0,
  "isRepeating": true
}
```

---

---

## Customer QR Identity & POS Attach

### Overview

Each customer carries a stable `CST-XXXXXXXXXXXX` QR code on their loyalty card / in-app screen. At the counter, the cashier scans it (USB scanner — keystrokes ending in Enter) and the in-flight POS order is linked to that customer. The existing `OrderCompletedEvent` listener then credits the wallet on payment-commit using the standard earn formula.

### Database

Migration **V145** adds `customers.qr_code`:

| Column   | Type        | Notes                                 |
|----------|-------------|---------------------------------------|
| qr_code  | VARCHAR(40) | NOT NULL, UNIQUE, indexed             |

Existing rows are backfilled on migrate. New rows generate a code in `Customer.@PrePersist` via `UUID.randomUUID()` truncated to 12 uppercase hex chars with a `CST-` prefix.

### Endpoints

#### Resolve scanned QR

```http
GET /api/v1/customers/by-qr/{qrCode}
Authorization: Bearer {token}
```

**Required role**: `ADMIN`, `MANAGER`, `OPERATOR`, `WAITER`, or `CASHIER`.

**Response** (200):

```json
{
  "success": true,
  "data": {
    "id": 123,
    "firstName": "Ada",
    "lastName": "Lovelace",
    "phone": "+998901234567",
    "qrCode": "CST-ABCDEF123456",
    "bonusBalance": 50000.00,
    "tierName": "Bronze",
    "orderCount": 12
  }
}
```

Returns 404 when the code doesn't exist.

#### Rotate QR code

```http
POST /api/v1/customers/{id}/qr-code/regenerate
Authorization: Bearer {token}
```

**Required role**: `ADMIN`, `OWNER`, or `MANAGER`. Use this when a customer reports a lost card or suspected fraud.

#### Attach customer to in-flight POS order

```http
PATCH /api/v1/pos/orders/{orderId}/customer
Authorization: Bearer {token}
Content-Type: application/json

{
  "qrCode": "CST-ABCDEF123456"
}
```

**Body**: exactly one of `customerId`, `qrCode`, `phone` (DTO-enforced via `@AssertTrue`). The cashier picks whichever path is fastest — usually QR scan; falls back to phone lookup when the card isn't to hand.

**Required role**: `ADMIN`, `OPERATOR`, `WAITER`, `CASHIER`, or `MANAGER`.

**Constraints**:
- Order must exist.
- Order must NOT be in a terminal status (`DELIVERED`, `COMPLETED`, `CANCELLED`) — admins cannot retro-attach to closed sales.

**Behaviour**: sets `order.customer`, saves, and returns the refreshed `POSOrderResponse`. No bonus is credited at this step — that happens on payment-commit via the existing `OrderCompletedEvent` path.

### Idempotency

Attaching the same customer twice is a no-op (just re-saves). Attaching a different customer overwrites the link. The wallet credit on order completion uses the existing `"order-bonus-{orderId}"` idempotency key on the `BonusTransaction` — unchanged by this work, so double-payment events still credit only once.

---

## Customer Wallet Top-Ups

### Overview

Authenticated customers can top up their own loyalty wallet from the consumer API. Top-ups are settled by either an external payment provider (Click or Payme) via webhook, or manually by an admin for cash-at-counter scenarios. **Top-ups are non-refundable promo credit, not a stored-value liability** — once credited, the balance behaves identically to earned bonus.

### Database

Migration **V146** adds the `wallet_top_ups` table:

| Column                    | Type          | Notes                                                                 |
|---------------------------|---------------|-----------------------------------------------------------------------|
| id                        | BIGSERIAL     | Primary key                                                           |
| customer_id               | BIGINT        | FK to `customers`, CASCADE delete                                     |
| amount                    | NUMERIC(12,2) | Positive (CHECK constraint)                                           |
| status                    | VARCHAR(20)   | `PENDING`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`              |
| provider                  | VARCHAR(20)   | `CLICK`, `PAYME`, `MANUAL`                                            |
| payment_url               | TEXT          | Hosted checkout URL (null for `MANUAL`)                               |
| external_transaction_id   | VARCHAR(120)  | Provider's tx id; UNIQUE per `(provider, external_transaction_id)`    |
| idempotency_key           | VARCHAR(120)  | UNIQUE                                                                |
| bonus_transaction_id      | BIGINT        | FK to `bonus_transactions` once settled                               |
| failure_reason            | TEXT          | Populated when status = FAILED                                        |
| metadata                  | JSONB         | Provider-supplied details (clickTransId, paymeTransactionId, etc.)    |
| completed_at              | TIMESTAMPTZ   | Set when status transitions to COMPLETED                              |

A `BonusTransaction.TransactionType.TOP_UP` value was added; it credits the wallet just like `EARNED` but with no `order_id` link and a `topup-{id}` idempotency key.

### Lifecycle

```
              create()                            webhook / admin confirm()
                  │                                          │
                  ▼                                          ▼
   ┌─────────┐         ┌────────────────────────────────────────┐
   │ (none)  │ ──────▶ │             PENDING                    │
   └─────────┘         └────────┬─────────────────┬─────────────┘
                                │                 │
                  cancel() ◀────┘                 └───▶ complete()
                                                            │
                                                            ▼
                       ┌────────────┐                ┌────────────┐
                       │ CANCELLED  │                │ COMPLETED  │
                       └────────────┘                └────────────┘
                                                            ▲
                                                            │ idempotent
                                                            │ retry no-op
                                       webhook error / admin fail()
                                                  │
                                                  ▼
                                            ┌──────────┐
                                            │  FAILED  │
                                            └──────────┘
```

All terminal states (`COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`) reject further state transitions, with two exceptions: a duplicate `complete()` call on an already-COMPLETED row is a deliberate no-op (so webhook retries are safe), and `fail()` on a terminal row is also a no-op.

### Consumer endpoints

All endpoints under `/api/v1/consumer/wallet/` require the customer's OTP-issued Bearer token (`CustomerPrincipal`).

#### Get own wallet

```http
GET /api/v1/consumer/wallet
Authorization: Bearer {customer_token}
```

Returns the customer's `CustomerLoyaltyResponse` — same shape as the admin endpoint.

#### Create top-up

```http
POST /api/v1/consumer/wallet/top-ups
Authorization: Bearer {customer_token}
Content-Type: application/json

{
  "amount": 50000,
  "provider": "CLICK"
}
```

**Validation**: `amount >= 1000`. `provider` must be one of `CLICK`, `PAYME`, `MANUAL`.

**Response** (201):

```json
{
  "success": true,
  "message": "Top-up created",
  "data": {
    "id": 42,
    "customerId": 7,
    "amount": 50000.00,
    "status": "PENDING",
    "provider": "CLICK",
    "paymentUrl": "https://my.click.uz/services/pay?service_id=...&merchant_id=...&amount=50000&transaction_param=42",
    "externalTransactionId": null,
    "failureReason": null,
    "createdAt": "2026-06-08T07:42:00Z",
    "completedAt": null
  }
}
```

The mobile / web client redirects the customer to `paymentUrl`. After they pay on the provider's hosted checkout, the provider POSTs to the corresponding webhook (`/webhook/wallet/click/*` or `/webhook/wallet/payme`) and the wallet is credited.

#### Get top-up status

```http
GET /api/v1/consumer/wallet/top-ups/{id}
```

Ownership-enforced — returns 404 if the top-up belongs to another customer.

#### List own top-ups

```http
GET /api/v1/consumer/wallet/top-ups?page=0&size=20
```

Newest first.

#### Cancel a pending top-up

```http
POST /api/v1/consumer/wallet/top-ups/{id}/cancel
```

Only valid while status is `PENDING`. Returns 400 with a clear message otherwise.

### Admin endpoints

Under `/api/v1/loyalty/wallet/`, role-gated.

#### List all top-ups

```http
GET /api/v1/loyalty/wallet/top-ups?status=PENDING&page=0&size=20
```

Optional `status` filter. **Required role**: `ADMIN`, `OWNER`, `MANAGER`.

#### Manually confirm (cash at counter)

```http
POST /api/v1/loyalty/wallet/top-ups/{id}/confirm?reference=cash-receipt-1234
```

Credits the wallet via the same idempotent path the webhooks use. Use for `MANUAL` provider top-ups, or to unstick a `PENDING` row where the webhook never arrived. **Required role**: `ADMIN`, `OWNER`, `MANAGER`, `CASHIER`.

#### Mark failed

```http
POST /api/v1/loyalty/wallet/top-ups/{id}/fail?reason=Card%20declined
```

No wallet movement. **Required role**: `ADMIN`, `OWNER`, `MANAGER`.

### Provider webhooks

Under `/api/v1/webhook/wallet/`. These bypass Bearer authentication (configured in `SecurityConfig.permitAll`) — they're protected by **provider signature verification**.

#### Click — 2-phase protocol

```http
POST /api/v1/webhook/wallet/click/prepare
Content-Type: application/x-www-form-urlencoded

click_trans_id=…&service_id=…&merchant_trans_id={topUpId}&amount=…&action=0&sign_time=…&sign_string=…
```

Click asks "ready to accept?" — we verify signature, lookup the top-up by `merchant_trans_id`, check it's `PENDING`, and verify the amount matches. Returns `error=0` and the `merchant_prepare_id` on success, or a negative error code otherwise.

```http
POST /api/v1/webhook/wallet/click/complete
Content-Type: application/x-www-form-urlencoded

click_trans_id=…&service_id=…&merchant_trans_id={topUpId}&merchant_prepare_id=…&amount=…&action=1&error=0&sign_time=…&sign_string=…
```

When `error=0`, calls `WalletTopUpService.complete()` and returns `merchant_confirm_id` + `error=0`. When `error != 0`, calls `fail()` and echoes back the error code.

**Signature**: MD5 of `click_trans_id + service_id + secret_key + merchant_trans_id + merchant_prepare_id + amount + action + sign_time`. When `click.secret-key` is unset (dev / not yet provisioned) the check is skipped with a `WARN` log line.

#### Payme — JSON-RPC

```http
POST /api/v1/webhook/wallet/payme
Authorization: Basic <base64("Paycom:{merchant_key}")>
Content-Type: application/json

{
  "id": 1,
  "method": "PerformTransaction",
  "params": {
    "id": "payme-transaction-id",
    "account": { "top_up_id": "42" }
  }
}
```

Currently only `PerformTransaction` is wired — it calls `complete()` and returns the canonical Payme success envelope (`result.transaction`, `result.state=2`, `result.perform_time`).

Other methods (`CheckPerformTransaction`, `CreateTransaction`, `CancelTransaction`, `CheckTransaction`, `GetStatement`) return Payme error code `-32601` ("method not implemented in this build") until the full state machine is wired in a follow-up. The skeleton is in place — fill out the remaining method branches in `WalletTopUpWebhookController.payme()`.

**Auth**: HTTP Basic with username `Paycom` and password = `payme.merchant-key`. When the key is unset the check is skipped with a `WARN` log.

### Configuration properties

All have placeholder defaults so the code wires in dev and tests. **Production deploys MUST set these** before going live with real customers.

```properties
# Click
click.merchant-id=...
click.service-id=...
click.secret-key=...
click.checkout-base-url=https://my.click.uz/services/pay

# Payme
payme.merchant-id=...
payme.merchant-key=...
payme.account-field=top_up_id
payme.checkout-base-url=https://checkout.paycom.uz
```

### Idempotency

- `BonusTransaction.idempotencyKey = "topup-{topUpId}"` — handled by `BonusService.recordTransaction`. Second call with the same key returns the existing transaction without re-crediting.
- `WalletTopUpService.complete()` short-circuits on `status == COMPLETED` before calling `BonusService`. Combined, a webhook can fire any number of times for the same top-up and the customer's balance changes exactly once.
- The `wallet_top_ups` UNIQUE partial index on `(provider, external_transaction_id) WHERE external_transaction_id IS NOT NULL` is a belt-and-braces guard against the (provider-side) bug where the same external tx is replayed against a fresh top-up.

### Source

- Entity / repo: `src/main/java/com/elcafe/modules/loyalty/entity/WalletTopUp.java`, `repository/WalletTopUpRepository.java`
- Service: `src/main/java/com/elcafe/modules/loyalty/service/WalletTopUpService.java`
- Provider strategies: `src/main/java/com/elcafe/modules/loyalty/service/topup/`
- Controllers: `src/main/java/com/elcafe/modules/loyalty/controller/ConsumerWalletController.java`, `WalletTopUpWebhookController.java`
- Migration: `src/main/resources/db/migration/V146__wallet_top_ups.sql`
- Tests: `src/test/java/com/elcafe/modules/loyalty/service/WalletTopUpServiceTest.java`, `controller/WalletTopUpWebhookControllerTest.java`

---

## Support

For questions or issues:

- **Documentation**: `/docs/API_REFERENCE.md`
- **Database Schema**: `/src/main/resources/db/migration/V27__*.sql`, `/src/main/resources/db/migration/V101__*.sql`, `/src/main/resources/db/migration/V145__customer_qr_code.sql`, `/src/main/resources/db/migration/V146__wallet_top_ups.sql`
- **Source Code**: `/src/main/java/com/elcafe/modules/loyalty/`

**Version:** 1.2
**Last Updated:** 2026-06-08
