package com.elcafe.modules.billing.service;

/**
 * Typed surface over the JSONB feature-code strings stored on {@link
 * com.elcafe.modules.billing.entity.SubscriptionPlan#getFeatureCodes()}.
 *
 * <p>Gating is at the sidebar <em>sub-item</em> granularity (e.g. {@code kitchen.dashboard}) so a
 * top-level group can stay visible while only some of its sub-items are unlocked. These constants
 * are the stable contract the frontend and the gating sweep (mini-phase A4) reference; no service
 * gates consume them yet (mini-phase A2 is service + admin API only). The actual module-to-tier
 * mapping is still open — when finalised, A4 seeds each plan's {@code feature_codes} from this list.
 */
public final class PlanFeature {

    private PlanFeature() {}

    // Dashboard
    public static final String DASHBOARD_FINANCIAL   = "dashboard.financial";
    public static final String DASHBOARD_OPERATIONAL = "dashboard.operational";
    public static final String DASHBOARD_CUSTOMER    = "dashboard.customer";
    public static final String DASHBOARD_INVENTORY   = "dashboard.inventory";

    // Point of Sale
    public static final String POS = "pos";

    // Orders
    public static final String ORDERS_HISTORY = "orders.history";
    public static final String ORDERS_ONLINE  = "orders.online";

    // Restaurant
    public static final String RESTAURANT_BRANCHES     = "restaurant.branches";
    public static final String RESTAURANT_TABLES       = "restaurant.tables";
    public static final String RESTAURANT_WORKING_HOURS = "restaurant.working_hours";
    public static final String RESTAURANT_RESERVATIONS = "restaurant.reservations";

    // Clients
    public static final String CLIENTS_CUSTOMERS = "clients.customers";
    public static final String CLIENTS_SEGMENTS  = "clients.segments";

    // Employees
    public static final String EMPLOYEES_OPERATORS   = "employees.operators";
    public static final String EMPLOYEES_WAITERS     = "employees.waiters";
    public static final String EMPLOYEES_SHIFTS      = "employees.shifts";
    public static final String EMPLOYEES_CONSUMPTION = "employees.consumption";
    public static final String EMPLOYEES_COURIERS    = "employees.couriers";

    // Catalog
    public static final String CATALOG_PRODUCTS    = "catalog.products";
    public static final String CATALOG_MENU        = "catalog.menu";
    public static final String CATALOG_CATEGORIES  = "catalog.categories";
    public static final String CATALOG_COLLECTIONS = "catalog.collections";

    // Kitchen
    public static final String KITCHEN_DASHBOARD    = "kitchen.dashboard";
    public static final String KITCHEN_INVENTORY    = "kitchen.inventory";
    public static final String KITCHEN_RECIPES      = "kitchen.recipes";
    public static final String KITCHEN_EXPIRY       = "kitchen.expiry";
    public static final String KITCHEN_STOCK_COUNTS = "kitchen.stock_counts";
    public static final String KITCHEN_WASTE        = "kitchen.waste";
    public static final String KITCHEN_SUPPLIERS    = "kitchen.suppliers";
    public static final String KITCHEN_PRODUCTION   = "kitchen.production";

    // Marketing
    public static final String MARKETING_PROMOTIONS = "marketing.promotions";
    public static final String MARKETING_COUPONS    = "marketing.coupons";
    public static final String MARKETING_HAPPY_HOURS = "marketing.happy_hours";
    public static final String MARKETING_BUNDLES    = "marketing.bundles";
    public static final String MARKETING_REFERRALS  = "marketing.referrals";
    public static final String MARKETING_SMS        = "marketing.sms";
    public static final String MARKETING_TELEGRAM   = "marketing.telegram";
    public static final String MARKETING_INSTAGRAM  = "marketing.instagram";
    public static final String MARKETING_REVIEWS    = "marketing.reviews";

    // Finance
    public static final String FINANCE_PURCHASE_ORDERS = "finance.purchase_orders";
    public static final String FINANCE_EXPENSES        = "finance.expenses";
    public static final String FINANCE_REPORTS         = "finance.reports";
    public static final String FINANCE_PRICING         = "finance.pricing";
    public static final String FINANCE_PAYROLL         = "finance.payroll";

    // Settings
    public static final String SETTINGS_USERS    = "settings.users";
    public static final String SETTINGS_PRINTERS = "settings.printers";
}
