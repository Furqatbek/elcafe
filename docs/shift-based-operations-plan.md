# Shift-Based Operations — Implementation Plan

## Current State

The system already has:
- ✅ `EmployeeShift` entity with clock in/out, breaks, cash reconciliation
- ✅ `ShiftManagementService` with full shift lifecycle
- ✅ `ShiftTimeService` for financial reporting with midnight-crossing support
- ✅ Cash drawer operations linked to shifts
- ✅ `ShiftStatus` enum: ACTIVE → ON_BREAK → COMPLETED → APPROVED
- ✅ Break tracking (BREAK, MEAL, RESTROOM, SMOKING)
- ✅ End-of-day report generation
- ✅ `WorkingHours` for recurring schedules
- ✅ Shift management API endpoints

## What's Missing

To run the restaurant **fully shift-based**, the following gaps need to be filled:

---

## Phase 1: POS Shift Enforcement

**Problem:** Currently the POS works without an active shift. Anyone can take orders without clocking in.

### What to Build

**Backend: Shift enforcement middleware**

When taking orders via POS, the system should:
1. Require an active shift for the operator
2. Link every order to the active shift
3. Block POS operations if no shift is active

```java
// In POSOrderService.createOrder() — add at the beginning:
EmployeeShift activeShift = shiftService.getActiveShiftForUser(currentUserId, restaurantId);
if (activeShift == null) {
    throw new IllegalStateException("No active shift. Please clock in first.");
}
order.setShiftId(activeShift.getId());
```

**Database:**
```sql
ALTER TABLE orders ADD COLUMN shift_id BIGINT REFERENCES employee_shifts(id);
```

**Frontend: Clock-in gate on POS**

Before showing the POS start screen, check for active shift:
```
┌─────────────────────────────────────────┐
│ No Active Shift                         │
│                                         │
│ You must clock in before using the POS  │
│                                         │
│ [Clock In]                              │
│                                         │
│ Or enter your PIN: [____]               │
└─────────────────────────────────────────┘
```

If active shift exists → proceed to POS as normal.

### Files to Modify
| File | Change |
|------|--------|
| `Order.java` | Add `shiftId` field |
| Migration V118 | Add `shift_id` to orders table |
| `POSOrderService.java` | Require active shift, link to order |
| `POSApp.jsx` | Check for active shift before showing POS |
| `posStore.js` | Store active shift info |

---

## Phase 2: Shift Dashboard (Real-Time)

**Problem:** No real-time visibility into who's working, on break, or approaching overtime.

### What to Build

**Admin page: `/admin/shift-dashboard`**

```
┌─────────────────────────────────────────────────────┐
│ Shift Dashboard          Today: May 1, 2026         │
├─────────────────────────────────────────────────────┤
│ Active Now: 5    │ On Break: 1    │ Total Hours: 32 │
├─────────────────────────────────────────────────────┤
│                                                     │
│ ● Ali (Cashier)    │ Since 08:00 │ 6h 30m │ Active │
│ ● Jasur (Waiter)   │ Since 09:00 │ 5h 30m │ Active │
│ ○ Dima (Cook)      │ Since 08:00 │ 6h 30m │ Break  │
│ ● Aziz (Waiter)    │ Since 12:00 │ 2h 30m │ Active │
│ ● Sasha (Cashier)  │ Since 14:00 │ 0h 30m │ Active │
│                                                     │
│ ⚠️ Ali approaching overtime (8h limit)             │
│                                                     │
├─────────────────────────────────────────────────────┤
│ Completed Today:                                    │
│ ✓ Bekzod (Waiter)  │ 08:00-14:00 │ 6h │ Approved  │
│ ✓ Nodira (Cashier) │ 07:00-15:00 │ 8h │ Pending   │
└─────────────────────────────────────────────────────┘
```

**Features:**
- Live timer for each active shift
- Break indicator with duration
- Overtime warning (configurable threshold)
- Quick actions: send to break, clock out
- WebSocket for real-time updates
- Shift history for the day

### Files to Create
| File | Description |
|------|-------------|
| `frontend/src/pages/ShiftDashboard.jsx` | Real-time shift overview |
| Route in App.jsx | `/admin/shift-dashboard` |
| Sidebar link | Under Operations section |

---

## Phase 3: Shift Scheduling (Plan Ahead)

**Problem:** `WorkingHours` only handles recurring weekly schedules. No way to plan specific shifts for specific dates (e.g., "Ali works 08:00-16:00 on May 5th").

### What to Build

**New entity: `ShiftSchedule`**

```sql
CREATE TABLE shift_schedules (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    employee_id BIGINT NOT NULL REFERENCES users(id),
    shift_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    role VARCHAR(50),            -- CASHIER, WAITER, COOK, etc.
    notes TEXT,
    status VARCHAR(20) DEFAULT 'SCHEDULED', -- SCHEDULED, CONFIRMED, CANCELLED
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Admin UI: Weekly schedule builder**

```
┌─────────────────────────────────────────────────────────────┐
│ Shift Schedule         Week: May 5-11, 2026   [< >]         │
├──────┬────────┬────────┬────────┬────────┬────────┬────────┤
│      │  Mon   │  Tue   │  Wed   │  Thu   │  Fri   │  Sat   │
├──────┼────────┼────────┼────────┼────────┼────────┼────────┤
│ Ali  │ 08-16  │ 08-16  │  OFF   │ 08-16  │ 08-16  │ 10-18  │
│ Jasur│ 09-17  │  OFF   │ 09-17  │ 09-17  │ 09-17  │ 09-17  │
│ Dima │ 07-15  │ 07-15  │ 07-15  │ 07-15  │  OFF   │  OFF   │
│ Aziz │ 12-20  │ 12-20  │ 12-20  │  OFF   │ 12-20  │ 12-22  │
└──────┴────────┴────────┴────────┴────────┴────────┴────────┘
                                            [+ Add Shift] [Publish]
```

**Features:**
- Drag-and-drop shift assignment
- Copy previous week
- Auto-fill from recurring WorkingHours
- Conflict detection (double-booking)
- Employee availability preferences
- Publish schedule (notifies employees)

---

## Phase 4: Shift-Based Financial Reports

**Problem:** Reports are date-based, not shift-based. Can't see "how much revenue did the morning shift generate vs evening shift?"

### What to Build

**Shift-based analytics:**

```
Revenue by Shift:
┌─────────────────────────────────────────────┐
│ Morning (07:00-15:00)  │ 3,500,000 │ 45%   │
│ Evening (15:00-23:00)  │ 4,200,000 │ 55%   │
├─────────────────────────────────────────────┤
│ Per Employee:                               │
│ Ali   │ 1,200,000 │ 42 orders │ ★ 4.8      │
│ Jasur │   890,000 │ 31 orders │ ★ 4.5      │
└─────────────────────────────────────────────┘

Labor Cost vs Revenue:
┌─────────────────────────────────────────────┐
│ Shift     │ Revenue  │ Labor Cost │ Ratio   │
│ Morning   │ 3,500K   │    450K    │ 12.8%   │
│ Evening   │ 4,200K   │    520K    │ 12.4%   │
└─────────────────────────────────────────────┘
```

**Backend queries:**
```java
// Orders grouped by shift
SELECT es.id, es.employee_id, es.clock_in, es.clock_out,
       COUNT(o.id) as order_count,
       SUM(o.total) as total_revenue
FROM employee_shifts es
LEFT JOIN orders o ON o.shift_id = es.id
WHERE es.restaurant_id = ? AND es.shift_date = ?
GROUP BY es.id
```

---

## Phase 5: Shift Handover Process

**Problem:** No formal handover between shifts. Cash, pending orders, and open tables need to be transferred.

### What to Build

**Shift handover checklist:**

```
┌─────────────────────────────────────────────┐
│ Shift Handover — Ali → Jasur               │
├─────────────────────────────────────────────┤
│ Cash Drawer:                                │
│   Expected: 1,250,000                       │
│   Counted:  [1,250,000]                     │
│   Variance: 0 ✓                            │
│                                             │
│ Open Tables: 3 (Tables 4, 7, 12)            │
│   → Transferred to Jasur                    │
│                                             │
│ Pending Orders: 2                           │
│   #ORD-045 (preparing), #ORD-046 (new)      │
│                                             │
│ Notes for next shift:                       │
│   [Table 7 waiting for dessert]             │
│                                             │
│ [Complete Handover]                         │
└─────────────────────────────────────────────┘
```

**Flow:**
1. Outgoing employee initiates handover
2. Count cash drawer → record variance
3. List open tables and pending orders
4. Transfer tables to incoming employee
5. Leave notes for next shift
6. Both employees confirm
7. Old shift → COMPLETED, new shift → ACTIVE

---

## Phase 6: Overtime & Labor Rules

**Problem:** No automatic overtime detection or labor rule enforcement.

### What to Build

**Configuration per restaurant:**

```sql
CREATE TABLE shift_rules (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    max_shift_hours INTEGER DEFAULT 8,
    max_weekly_hours INTEGER DEFAULT 40,
    overtime_multiplier DECIMAL(3,2) DEFAULT 1.50,
    min_break_after_hours INTEGER DEFAULT 4,    -- must take break after 4h
    min_break_duration_minutes INTEGER DEFAULT 30,
    notify_overtime_at_hours INTEGER DEFAULT 7,  -- warn 1h before max
    auto_clock_out_after_hours INTEGER DEFAULT 12, -- force clock out
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Enforcement:**
- ⚠️ Alert when approaching max hours
- ⚠️ Alert if break not taken after configured hours
- 🔴 Auto-clock-out if exceeded max (with notification)
- 📊 Weekly overtime report
- 💰 Overtime pay calculation in payroll

---

## Phase 7: Mobile Clock In/Out

**Problem:** Employees must use the admin panel to clock in. Need a simpler mobile-friendly option.

### What to Build

**Simple mobile page: `/shift/clock`**

```
┌─────────────────────────┐
│                         │
│    Good Morning, Ali    │
│                         │
│    ┌─────────────┐      │
│    │  Clock In   │      │
│    └─────────────┘      │
│                         │
│    PIN: [● ● ● ●]      │
│                         │
│    Or scan QR code      │
│                         │
└─────────────────────────┘

After clock in:
┌─────────────────────────┐
│    Ali — Active         │
│    Since: 08:00         │
│    Duration: 4h 32m     │
│                         │
│    [Take Break]         │
│    [Clock Out]          │
│                         │
└─────────────────────────┘
```

**Options for clock-in method:**
- PIN code (existing waiter auth pattern)
- QR code scan (employee scans restaurant QR)
- NFC (future — employee card tap)
- GPS verification (optional — must be at restaurant location)

---

## Phase 8: Shift Notifications

**Problem:** No alerts for shift events.

### Notifications to implement:

| Event | Who gets notified | Channel |
|-------|-------------------|---------|
| Shift starting in 30min | Employee | Push/Telegram |
| Employee late (10min past scheduled) | Manager | Push/Telegram |
| Break exceeded max duration | Manager | Push |
| Approaching overtime | Employee + Manager | Push |
| Shift handover needed | Next shift employee | Push |
| End-of-day report ready | Manager/Owner | Telegram |
| Schedule published | All affected employees | Push/Telegram |
| Shift swap request | Target employee | Push |

---

## Phase 9: Shift Swap & Coverage

**Problem:** Employees can't swap shifts or request coverage.

### What to Build

```sql
CREATE TABLE shift_swap_requests (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    requesting_employee_id BIGINT NOT NULL REFERENCES users(id),
    target_employee_id BIGINT REFERENCES users(id),  -- null = open request
    schedule_id BIGINT REFERENCES shift_schedules(id),
    shift_date DATE NOT NULL,
    reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED, APPROVED
    approved_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Flow:**
1. Employee requests swap (target specific person or open to all)
2. Target employee accepts/declines
3. Manager approves the swap
4. Schedule updated automatically

---

## Phase 10: Shift-Based Inventory Counts

**Problem:** No connection between shift start/end and inventory levels.

### What to Build

Optional start/end-of-shift inventory snapshot:

```
Shift Start (Ali, 08:00):
  Auto-record: key item levels (configurable items)
  
Shift End (Ali, 16:00):
  Compare: start vs end vs expected (based on orders)
  Flag: discrepancies for manager review
```

This ties inventory variance to specific shifts/employees — accountability.

---

## Implementation Priority

| Priority | Phase | Effort | Impact |
|----------|-------|--------|--------|
| 🔴 High | Phase 1: POS Enforcement | 2 days | Core — nothing works shift-based without this |
| 🔴 High | Phase 2: Shift Dashboard | 2 days | Visibility — managers need real-time view |
| 🟡 Medium | Phase 4: Shift Financial Reports | 2 days | Analytics — justify labor allocation |
| 🟡 Medium | Phase 5: Shift Handover | 2 days | Operations — smooth transitions |
| 🟡 Medium | Phase 7: Mobile Clock In | 1 day | UX — employees need easy clock in |
| 🟡 Medium | Phase 6: Overtime Rules | 2 days | Compliance — labor law requirements |
| 🟢 Low | Phase 3: Schedule Builder | 3 days | Planning — nice but can use spreadsheet |
| 🟢 Low | Phase 8: Notifications | 1 day | QoL — leverages existing notification infra |
| 🟢 Low | Phase 9: Shift Swap | 2 days | Employee happiness |
| 🟢 Low | Phase 10: Inventory Counts | 2 days | Advanced accountability |

---

## Summary

**Total: ~19 days of development for all 10 phases**

Start with Phase 1 (enforce shifts on POS) and Phase 2 (dashboard) — these form the foundation. Everything else builds on top.

The existing infrastructure (`EmployeeShift`, `ShiftManagementService`, `ShiftTimeService`, cash drawer ops) means 60% of the backend is already built. The main work is:
1. **Enforcement** — making shifts mandatory for POS
2. **Visibility** — real-time dashboard
3. **Intelligence** — reports, overtime detection, handover process
