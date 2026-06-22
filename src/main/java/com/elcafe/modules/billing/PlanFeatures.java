package com.elcafe.modules.billing;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature-code vocabulary for plan gating (mini-phase A4).
 *
 * <p>Only PAID capabilities carry a code. Core modules — POS, basic orders, menu/products/categories,
 * restaurant branches/tables/working-hours, customers, operators/waiters/shifts, and
 * system-users/printers/receipt-template — are in every tier and have <b>no</b> code, so they are
 * never gated. The {@code /subscription} page is likewise always reachable.
 *
 * <p>Tiers are cumulative: <b>Start</b> unlocks none of these (core only), <b>Advance</b> unlocks
 * {@link #ADVANCE}, and <b>Pro</b> unlocks {@link #ADVANCE} + {@link #PRO_ONLY}. The per-plan
 * assignment is seeded in {@code db/migration/V157__seed_plan_features.sql} — keep that file, this
 * class, and the frontend code list (frontend/src/config/planFeatures.js) in sync.
 *
 * <p>Granularity is one code per logical paid module; sub-items that always share a tier share a code
 * (e.g. all the inventory screens are {@link #INVENTORY}), and sub-items that sit in a higher tier get
 * their own code (e.g. {@link #KITCHEN_PRODUCTION}).
 */
public final class PlanFeatures {

    private PlanFeatures() {
    }

    // ---- Advance tier ----
    public static final String ANALYTICS = "analytics";                 // Dashboard analytics group
    public static final String ORDERS_ONLINE = "orders.online";         // Online / self-service orders
    public static final String RESERVATIONS = "reservations";
    public static final String CUSTOMER_SEGMENTS = "customers.segments";
    public static final String COURIERS = "couriers";                   // Couriers + courier map
    public static final String STAFF_PERFORMANCE = "staff.performance";  // Waiter performance
    public static final String STAFF_CONSUMPTION = "staff.consumption";  // Consumption + allowances
    public static final String MENU_COLLECTIONS = "menu.collections";
    public static final String KITCHEN = "kitchen";                     // Kitchen display (KDS)
    public static final String INVENTORY = "inventory";                 // Inventory, recipes, counts, waste, suppliers, alerts, valuation, expiry
    public static final String MARKETING = "marketing";                 // Promotions, coupons, happy hours, bundles, QR codes
    public static final String LOYALTY = "loyalty";                     // Loyalty & wallet
    public static final String REVIEWS = "reviews";
    public static final String FINANCE = "finance";                     // Purchase orders, expenses, reports, pricing, alerts
    public static final String KITCHEN_STATIONS = "kitchen.stations";
    public static final String TELEGRAM_SUBSCRIBERS = "telegram.subscribers"; // Owner-bot subscribers

    // ---- Pro tier (in addition to Advance) ----
    public static final String KITCHEN_PRODUCTION = "kitchen.production";
    public static final String INVENTORY_PO_SUGGESTIONS = "inventory.po_suggestions";
    public static final String MARKETING_REFERRALS = "marketing.referrals";
    public static final String MARKETING_SMS = "marketing.sms";
    public static final String MARKETING_TELEGRAM = "marketing.telegram";
    public static final String MARKETING_INSTAGRAM = "marketing.instagram";
    public static final String MARKETING_MILESTONES = "marketing.milestones";
    public static final String MARKETING_ANALYTICS = "marketing.analytics";
    public static final String PAYROLL = "payroll";

    /** Codes unlocked by Advance (and Pro). */
    public static final List<String> ADVANCE = List.of(
            ANALYTICS, ORDERS_ONLINE, RESERVATIONS, CUSTOMER_SEGMENTS, COURIERS, STAFF_PERFORMANCE,
            STAFF_CONSUMPTION, MENU_COLLECTIONS, KITCHEN, INVENTORY, MARKETING, LOYALTY, REVIEWS,
            FINANCE, KITCHEN_STATIONS, TELEGRAM_SUBSCRIBERS);

    /** Codes unlocked only by Pro (on top of {@link #ADVANCE}). */
    public static final List<String> PRO_ONLY = List.of(
            KITCHEN_PRODUCTION, INVENTORY_PO_SUGGESTIONS, MARKETING_REFERRALS, MARKETING_SMS,
            MARKETING_TELEGRAM, MARKETING_INSTAGRAM, MARKETING_MILESTONES, MARKETING_ANALYTICS, PAYROLL);

    /** Full Pro code set = Advance + Pro-only. */
    public static List<String> pro() {
        List<String> all = new ArrayList<>(ADVANCE);
        all.addAll(PRO_ONLY);
        return all;
    }
}
