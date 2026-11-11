# Dashboard maths

Every number returned by `GET /api/v1/dashboard/month?restaurantId={id}`, as the
code computes it. Source: `DashboardService.getDashboard` and `CogsService`.

The weekly and daily endpoints are the same function over a different window.

---

## 0. The period

```
endDate   = today
startDate = first day of the current month
```

The window is then widened to the restaurant's business hours, so a shift that
crosses midnight is not cut in half:

```
[shiftStart, shiftEnd] = ShiftTimeService.getShiftTimeRangeForPeriod(restaurantId, startDate, endDate)
```

**Orders** are selected on `created_at` inside `[shiftStart, shiftEnd]`.
**Expenses and payroll** are selected on calendar date inside
`[startDate, expenseEndDate]`, where

```
expenseEndDate = max(endDate, date(shiftEnd))
```

The two use different clocks, which is why an order and an expense recorded
minutes apart can land in different periods near a boundary.

## 1. Which orders count

```
allOrders       = orders in window
activeOrders    = allOrders  where  deleted_at IS NULL
completedOrders = activeOrders where status ≠ CANCELLED
                               and ( status ∈ REVENUE_STATUSES
                                     or fullyPaid
                                     or paymentStatus = COMPLETED )
soldItemOrders  = activeOrders where status ∉ {CANCELLED, REJECTED, PENDING}
```

```
REVENUE_STATUSES = { ACCEPTED, PREPARING, READY, PICKED_UP,
                     COURIER_ASSIGNED, ON_DELIVERY, DELIVERED, COMPLETED }
```

`completedOrders` drives money. `soldItemOrders` drives the product list, so
`totalItemsSold` can cover more orders than `totalIncome` does.

## 2. Revenue

```
totalIncome = Σ order.total          over completedOrders
```

`order.total` is the final charged amount — subtotal + tax + delivery + service
+ entry fee − discount. Discounts are therefore already deducted; there is no
separate discount line here.

## 3. Expenses

```
paidExpenses  = expenses in window where paymentStatus = PAID
expenseSum    = Σ expense.totalAmount           over paidExpenses
                where totalAmount = amount + taxAmount

paidPayroll   = payroll in window where status = PAID, by paymentDate
totalPayroll  = Σ payroll.netPay                over paidPayroll

totalExpenses = expenseSum + totalPayroll
```

Three things follow:

- **`totalExpenses` already contains `totalPayroll`.** Adding them together
  double counts the wages.
- Only `PAID` rows count. An unpaid expense is invisible here.
- Soft-deleted rows never appear: `Expense` carries
  `@SQLRestriction("deleted_at IS NULL")`, so anything voided or backfilled away
  (`V140_BACKFILL`, `V153_BACKFILL`) is excluded at the database level.

```
expensesByCategory = expenseSum grouped by expense.category
                     + { "PAYROLL": totalPayroll }   when totalPayroll > 0
```

So `Σ expensesByCategory.values() == totalExpenses`.

## 4. Cost of goods sold — reported, NOT subtracted

```
soldLines = items of completedOrders where deleted_at IS NULL and productId ≠ NULL
totalCOGS = Σ ( product.costPrice × line.quantity )   over soldLines
            counting only products whose costPrice > 0
```

`product.costPrice` is maintained by `ProductCostService` from the product's
inventory recipe, or from its production batches. A product with no recipe, or
whose ingredients carry no unit cost, has `costPrice = 0` and contributes
**nothing**.

Lines with `productId = NULL` — bundles and packaging such as cups — are skipped
entirely, which is why they can appear in the product list with no cost.

**`totalCOGS` is not part of profit.** It is reported for per-product margin
only. Stock is expensed when it is bought (section 3), so charging COGS as well
would count the same goods twice.

## 5. Profit

```
netProfit    = totalIncome − totalExpenses

profitMargin = 0                                    when totalIncome ≤ 0
             = netProfit / totalIncome × 100         otherwise
```

Cash basis: revenue less what was actually paid out, wages included. Expanded:

```
netProfit = totalIncome − expenseSum − totalPayroll
```

Worked against the figures for 2026-11-01 … 09:

```
  totalIncome    21,645,000.00
− expenseSum     11,900,638.95     (OTHER 847,638.95 + SUPPLIES 11,053,000)
− totalPayroll    5,860,000.00
= netProfit       +3,884,361.05     margin 17.95%

  totalCOGS       9,788,929.47     reported, not subtracted
```

The trade-off is inherent to the cash basis, not a defect: a period containing a
large restock looks bad and the period that sells that stock looks good. On
2026-11-06 alone, 9,290,003.89 of purchases met 2,135,000 of sales.

The alternative is to stop expensing purchases and subtract COGS instead:

```
netProfit = totalIncome − totalCOGS − overhead − totalPayroll
```

One or the other. Never both — that is what reported a 5.9M loss on a month
that made 3.9M.

## 6. Order statistics

```
totalOrders       = |activeOrders|          (cancelled included)
completedOrders   = |completedOrders|
cancelledOrders   = count of activeOrders with status = CANCELLED
averageOrderValue = totalIncome / completedOrders      rounded to 2dp
                  = 0                                   when completedOrders = 0
totalItemsSold    = Σ item.quantity    over non-deleted items of soldItemOrders
```

`totalItemsSold` counts packaging lines, so it exceeds the number of things a
customer chose. 483 cups in a period make it look about 1.6× the real figure.

## 7. Revenue splits

```
incomeByOrderType      = Σ order.total grouped by order.orderType       over completedOrders
incomeByPaymentMethod  = Σ order.total grouped by payment method        over completedOrders
```

Both sum to `totalIncome`.

## 8. Per-day breakdown

For each date `d` from `startDate` to `endDate`:

```
dayOrders    = completedOrders whose business day = d
               (business day, not calendar date — the shift-aware mapping)
dayExpenses  = paidExpenses where expenseDate = d

income       = Σ order.total              over dayOrders
cogs         = COGS of dayOrders          (section 4; reported only)
expenses     = Σ expense.totalAmount      over dayExpenses
orderCount   = |dayOrders|
netProfit    = income − expenses
```

Note what the daily rows do **not** include: payroll. `totalPayroll` is a
period figure keyed on payment date, so `Σ dailyStats.netProfit ≠ netProfit`
unless no payroll was paid in the period. The daily rows are a trading-day view;
the header is the full period.

### The daily rows can also lose income at the edges

Orders are bucketed by **business day**:

```java
groupingBy(o -> shiftTimeService.getBusinessDay(restaurantId, o.getCreatedAt().toLocalDateTime()))
```

The loop then walks `startDate … endDate`, so any order whose business day falls
outside that range is counted in `totalIncome` but appears in **no** daily row.
An order late on the last day whose business day maps to the following date is
simply dropped from the breakdown.

Measured on the 2026-11-01 … 09 response:

```
Σ dailyStats.income    21,240,000.00
totalIncome            21,645,000.00
missing                   405,000.00
```

The same 405,000 is why the profit reconciliation is off by more than payroll:

```
Σ dailyStats.netProfit  9,339,361.05
netProfit               3,884,361.05
difference              5,455,000.00  =  payroll 5,860,000 − income gap 405,000
```

Expenses reconcile exactly (`Σ dailyStats.expenses == expenseSum`), so only the
order-bucketing is affected. To see which orders fall out:

```sql
SELECT o.id, o.order_number, o.created_at, o.total
FROM orders o
WHERE o.restaurant_id = 1 AND o.deleted_at IS NULL
  AND o.created_at >= '2026-11-01' AND o.created_at < '2026-11-10'
  AND (o.created_at AT TIME ZONE 'Asia/Tashkent')::date
      NOT BETWEEN DATE '2026-11-01' AND DATE '2026-11-09'
ORDER BY o.created_at;
```

Treat the header as authoritative and the daily rows as indicative.

## 9. Per-product list

For each `productId` across `soldItemOrders`, over non-deleted lines:

```
quantitySold = Σ line.quantity
totalRevenue = Σ line.totalPrice
costPrice    = product.costPrice            (null when the product is gone)
totalCost    = costPrice × quantitySold
profit       = totalRevenue − totalCost
profitMargin = profit / totalRevenue × 100   when totalRevenue > 0
```

Enrichment is skipped when `productId` is null or `costPrice` is null, leaving
`costPrice`, `totalCost`, `profit` and `profitMargin` as `null`.

```
topLossLeaders = products with profit < 0, most negative first, max 10
lowMarginItems = products with 0 < profitMargin < 30,
                 ordered by totalCost descending, max 10
```

Both are only as good as `costPrice`. A product costed at zero reports a 100%
margin and never appears in either list.

## 10. Comparison with the previous period

```
days          = endDate − startDate + 1
prevEndDate   = startDate − 1 day
prevStartDate = prevEndDate − (days − 1)
```

The previous window is the same length immediately before this one — so early in
a month the month-to-date figure is compared against an equally short slice of
the previous month, not against a whole month.

```
incomeChange  = pctChange(prevIncome,  totalIncome)
expenseChange = pctChange(prevExpenses, totalExpenses)
profitChange  = pctChange(prevIncome − prevExpenses, totalIncome − totalExpenses)

orderCountChange = (currentOrders − prevOrders) × 100 / prevOrders     integer division
                 = 0                                                    when prevOrders = 0

pctChange(prev, cur) = 100               when prev = 0 and cur > 0
                     = 0                 when prev = 0 and cur ≤ 0
                     = (cur − prev) / |prev| × 100     otherwise

trend = "UP"      when profitChange >  5
      = "DOWN"    when profitChange < −5
      = "STABLE"  otherwise
```

`|prev|` is deliberate: it keeps the sign of the change meaningful when the
previous period was a loss.

## 11. Inventory alerts

Over active ingredients of the restaurant:

```
CRITICAL     when currentStock ≤ 0
LOW          when currentStock ≤ minimumStock
reorderCount counts currentStock ≤ reorderLevel
```

Independent of every money figure above.

---

## Reading the response safely

- `totalExpenses` **includes** `totalPayroll` — never add them.
- `totalCOGS` is **not** in `netProfit`. `totalIncome − totalExpenses` is the
  whole of it.
- `Σ dailyStats.netProfit ≠ netProfit` whenever payroll was paid in the period.
- `totalItemsSold` counts packaging.
- Anything derived from `costPrice` — `totalCOGS`, `profit`, `profitMargin`,
  `topLossLeaders`, `lowMarginItems` — is only as accurate as the product
  recipes. Check for unpriced products before trusting a margin:

  ```sql
  SELECT id, name, price, cost_price FROM products
  WHERE cost_price IS NULL OR cost_price = 0 ORDER BY name;
  ```
