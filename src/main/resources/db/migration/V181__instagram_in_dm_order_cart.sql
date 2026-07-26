-- Wave 7: in-progress cart for Instagram in-DM ordering.
--
-- A REGISTERED Instagram subscriber can now build an order across several DM turns (pick item → set
-- quantity → add more → checkout). The in-progress cart is kept on the subscriber's own row as a single
-- JSONB document rather than a parallel cart-entity system (unlike SelfServiceOrderService, whose cart
-- lives in dedicated self_service_cart_item rows) — the DM cart is short-lived conversation state, one
-- open cart per subscriber at a time, cleared the moment the order is placed or cancelled.
--
--   * order_cart JSONB, nullable — NULL means "no order in progress" (the default and the state a
--     finished/cancelled order resets to). Maps to InstagramSubscriber.orderCart, a
--     List<InstagramCartLine> under @JdbcTypeCode(SqlTypes.JSON) (Hibernate's dialect-aware JSON type,
--     jsonb here, a JSON-typed VARCHAR under the H2 test dialect — never a literal columnDefinition).
--     Each line carries productId, productName, unitPrice and quantity: enough to render the cart back
--     and to build the Order's OrderItems at checkout, denormalised so a since-deleted/renamed product
--     never corrupts an in-flight cart.
--
-- No index: the column is only ever read/written by primary key (the subscriber resolved from the
-- inbound DM's igsid + restaurant_id), never queried across.
ALTER TABLE instagram_subscribers ADD COLUMN IF NOT EXISTS order_cart JSONB;

COMMENT ON COLUMN instagram_subscribers.order_cart IS 'In-progress Instagram in-DM order cart (Wave 7): a JSON array of {productId, productName, unitPrice, quantity} lines, or NULL when no order is in progress. Cleared on checkout/cancel.';
