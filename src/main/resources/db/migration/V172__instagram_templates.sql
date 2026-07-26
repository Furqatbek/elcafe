-- V172: Instagram DM message templates (per-tenant channel).
--
-- Mirrors telegram_templates (V64) for the Instagram channel: a reusable message body with
-- {placeholder} substitution, an optional image, and optional button/quick-reply configuration.
--
-- Like instagram_campaign (V166), this table is per-tenant from birth (no V163-style retrofit
-- needed): restaurant_id is NOT NULL with an FK to restaurants ON DELETE CASCADE, and the entity
-- carries the §3.4 restaurantFilter. A template name only needs to be unique within one restaurant's
-- own library, so the uniqueness constraint is scoped to (restaurant_id, name) rather than global —
-- unlike telegram_templates, where "name" is globally unique because that table predates per-tenant
-- scoping.

CREATE TABLE instagram_templates (
    id             BIGSERIAL    PRIMARY KEY,
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    message_text   TEXT         NOT NULL,           -- Message with placeholders: {name}, {code}, {amount}, etc.
    image_url      VARCHAR(500),
    has_image      BOOLEAN      NOT NULL DEFAULT false,
    has_buttons    BOOLEAN      NOT NULL DEFAULT false,
    buttons_config JSONB,                            -- Button configuration: [{text: "Order Now", url: "..."}, ...]
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    usage_count    INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uq_instagram_templates_restaurant_name UNIQUE (restaurant_id, name)
);

-- Tenant-leading, for the restaurantFilter and the paged "list this restaurant's templates" query.
CREATE INDEX idx_instagram_templates_restaurant ON instagram_templates(restaurant_id);
