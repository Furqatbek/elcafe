-- V184: the restaurant floor map — a drawable plan of the room rather than a list of tables.
--
-- restaurant_tables has carried position_x/position_y/table_width/table_height since V25, but nothing
-- ever rendered them: the Tables page draws a filtered grid of cards. This migration adds what a real
-- plan needs on top of that existing geometry — a shape and rotation per table, furniture that is not a
-- table at all, sections you can draw, and more than one map per restaurant.
--
-- One migration rather than several because the backfill only makes sense atomically: the moment
-- restaurant_tables gains floor_plan_id, every existing table must already have a plan to point at, or
-- the current data disappears from a map that cannot show unassigned tables.

-- ---------------------------------------------------------------------------------------------------
-- A restaurant has several maps: ground floor, upstairs, summer terrace.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE floor_plan (
    id             BIGSERIAL    PRIMARY KEY,
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    -- Where this map sits in the switcher. Not the id, so plans can be reordered without renumbering.
    display_order  INTEGER      NOT NULL DEFAULT 0,
    -- The map opened when no other choice is remembered. Exactly one per restaurant, enforced by the
    -- partial unique index below rather than by application code that could be bypassed.
    is_default     BOOLEAN      NOT NULL DEFAULT false,
    -- The drawable canvas in the same abstract units as table position_x/position_y. Stored per plan
    -- because a terrace and a dining room are not the same size, and the editor needs bounds to snap
    -- and clamp against.
    canvas_width   INTEGER      NOT NULL DEFAULT 1200,
    canvas_height  INTEGER      NOT NULL DEFAULT 800,
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ,
    CONSTRAINT uq_floor_plan_restaurant_name UNIQUE (restaurant_id, name)
);

CREATE INDEX idx_floor_plan_restaurant ON floor_plan(restaurant_id, display_order);

-- At most one default per restaurant. A partial unique index says this precisely; a plain UNIQUE would
-- also forbid two NON-default plans, which is the normal case.
CREATE UNIQUE INDEX uq_floor_plan_one_default
    ON floor_plan(restaurant_id) WHERE is_default;

-- Every restaurant gets a map, so the page is never empty on first open and the backfill below has
-- somewhere to put existing tables.
INSERT INTO floor_plan (restaurant_id, name, display_order, is_default)
SELECT id, 'Main floor', 0, true FROM restaurants;

-- ---------------------------------------------------------------------------------------------------
-- Tables gain the properties a drawn plan needs.
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE restaurant_tables
    -- RECTANGLE | ROUNDED | OVAL | SQUARE — how the corners are drawn, which is what the operator asked
    -- for. Defaulting to RECTANGLE keeps every existing table looking the way the old card list implied.
    ADD COLUMN IF NOT EXISTS shape         VARCHAR(20) NOT NULL DEFAULT 'RECTANGLE',
    ADD COLUMN IF NOT EXISTS rotation_deg  INTEGER     NOT NULL DEFAULT 0,
    -- Draw order. A table pulled alongside a sofa has to be able to sit above or below it.
    ADD COLUMN IF NOT EXISTS z_index       INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS floor_plan_id BIGINT      REFERENCES floor_plan(id) ON DELETE SET NULL;

-- Existing tables move onto their restaurant's default plan, so nothing is orphaned and the current
-- room keeps working the moment the map ships.
UPDATE restaurant_tables t
SET floor_plan_id = p.id
FROM floor_plan p
WHERE p.restaurant_id = t.restaurant_id
  AND p.is_default
  AND t.floor_plan_id IS NULL;

CREATE INDEX idx_restaurant_tables_floor_plan ON restaurant_tables(floor_plan_id);

-- ---------------------------------------------------------------------------------------------------
-- Furniture that is not a table: sofas, chairs, plants, doors, the bar itself.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE floor_object (
    id             BIGSERIAL    PRIMARY KEY,
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    -- Belongs to a PLAN, not to the restaurant: each map is drawn independently, so deleting a terrace
    -- takes its furniture with it rather than leaving it floating on the ground floor.
    floor_plan_id  BIGINT       NOT NULL REFERENCES floor_plan(id) ON DELETE CASCADE,
    object_type    VARCHAR(30)  NOT NULL,   -- SOFA | CHAIR | PLANT | DOOR | BAR | WALL | OTHER
    label          VARCHAR(100),
    position_x     INTEGER      NOT NULL DEFAULT 0,
    position_y     INTEGER      NOT NULL DEFAULT 0,
    width          INTEGER      NOT NULL DEFAULT 60,
    height         INTEGER      NOT NULL DEFAULT 60,
    rotation_deg   INTEGER      NOT NULL DEFAULT 0,
    shape          VARCHAR(20)  NOT NULL DEFAULT 'RECTANGLE',
    z_index        INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

CREATE INDEX idx_floor_object_plan ON floor_object(floor_plan_id);

-- ---------------------------------------------------------------------------------------------------
-- Drawn areas: the bar corner, the VIP room, the smoking terrace.
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE floor_section (
    id             BIGSERIAL    PRIMARY KEY,
    restaurant_id  BIGINT       NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    floor_plan_id  BIGINT       NOT NULL REFERENCES floor_plan(id) ON DELETE CASCADE,
    name           VARCHAR(100) NOT NULL,
    -- An arbitrary polygon as [{x,y},…]. restaurant_tables.section is a free-text LABEL and stays that
    -- way; this is the drawn shape, which a label cannot express. Kept as JSON rather than PostGIS
    -- geometry because nothing here does spatial queries — it is drawn, hit-tested in the browser, and
    -- never intersected server-side.
    polygon        JSONB        NOT NULL,
    fill_color     VARCHAR(20),
    z_index        INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ
);

CREATE INDEX idx_floor_section_plan ON floor_section(floor_plan_id);

COMMENT ON TABLE floor_plan IS
    'One drawable map of a room (V184). A restaurant has several: floors, halls, terraces.';
COMMENT ON TABLE floor_object IS
    'Non-table furniture on a floor plan (V184): sofas, chairs, plants, doors, bar fixtures.';
COMMENT ON TABLE floor_section IS
    'A drawn area on a floor plan (V184) — the bar corner, a VIP room. Polygon, not a text label.';
COMMENT ON COLUMN restaurant_tables.floor_plan_id IS
    'Which map this table is drawn on (V184). NULL means it exists but is not placed on any plan.';
