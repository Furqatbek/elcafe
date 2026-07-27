-- V183: structured guest preferences — what they like, dislike, must avoid.
--
-- Customers already carry free-text `notes` and `tags`, and both are where this information ends up
-- today: "hates coriander, allergic to walnuts" buried in a sentence. That is fine to read and useless
-- to act on — you cannot ask "which of my guests are vegetarian" of a paragraph, and a waiter cannot be
-- warned about an allergy the system never modelled.
--
-- So this is a table rather than a JSON blob on customers: preferences are the thing you will want to
-- FILTER and SEGMENT on (a campaign to everyone who likes desserts, a kitchen warning for an allergy),
-- and a blob makes every one of those a full scan plus application-side parsing.
--
-- ALLERGY is deliberately its own type rather than a flavour of DISLIKE. They read the same in a list
-- and mean very different things: one is a preference, the other is a safety note, and the UI is
-- expected to treat them differently.

CREATE TABLE customer_preference (
    id                BIGSERIAL    PRIMARY KEY,
    restaurant_id     BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    -- CASCADE: a preference is pure PII about one person. When the customer is erased under the
    -- existing deletion path these must go with them, without the application having to remember.
    customer_id       BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    preference_type   VARCHAR(20)  NOT NULL,   -- LIKE | DISLIKE | ALLERGY | DIETARY
    -- NOT named `value`: Postgres allows it, H2 does not (it is reserved there), and the whole test
    -- suite builds its schema on H2 — so a bare `value` column compiles here and breaks every
    -- @DataJpaTest that touches this table.
    preference_value  VARCHAR(120) NOT NULL,
    note              VARCHAR(500),
    -- MANUAL = a person typed it. Kept for a later DERIVED source (inferred from order history) so the
    -- two can never be confused: a guess must never be shown as something the guest actually told you.
    source            VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    created_by_user_id BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    -- The same fact recorded twice is noise, and worse on an allergy list where a stale duplicate
    -- suggests disagreement. One row per (guest, kind, thing).
    CONSTRAINT uq_customer_preference UNIQUE (customer_id, preference_type, preference_value)
);

-- The profile page's own read: every preference for one guest.
CREATE INDEX idx_customer_preference_customer ON customer_preference(customer_id);

-- Tenant-leading, for the restaurantFilter and for segmenting a restaurant's guests by preference
-- ("everyone here who is vegetarian") without touching another tenant's rows.
CREATE INDEX idx_customer_preference_restaurant_type ON customer_preference(restaurant_id, preference_type);

COMMENT ON TABLE customer_preference IS
    'Structured guest preferences (V183): likes, dislikes, allergies, dietary needs. Erased with the customer.';
