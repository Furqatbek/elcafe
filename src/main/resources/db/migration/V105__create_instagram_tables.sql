-- Instagram integration tables

-- Bot / app credentials (one active config at a time)
CREATE TABLE instagram_bot_config (
    id                   BIGSERIAL PRIMARY KEY,
    app_id               VARCHAR(50),
    app_secret           VARCHAR(200),
    access_token         TEXT,           -- long-lived Page Access Token
    instagram_account_id VARCHAR(50),    -- numeric IG Business Account ID
    verify_token         VARCHAR(200),   -- random string for webhook challenge
    is_active            BOOLEAN DEFAULT false,
    welcome_message      TEXT,
    auto_reply_enabled   BOOLEAN DEFAULT false,
    auto_reply_template  TEXT,           -- comment auto-reply text
    created_at           TIMESTAMP DEFAULT NOW(),
    updated_at           TIMESTAMP DEFAULT NOW()
);

-- Subscribers who messaged the bot (keyed by Instagram Scoped ID)
CREATE TABLE instagram_subscribers (
    id                 BIGSERIAL PRIMARY KEY,
    igsid              VARCHAR(50) NOT NULL UNIQUE, -- Instagram Scoped User ID
    username           VARCHAR(100),
    display_name       VARCHAR(200),    -- entered by user in wizard
    phone              VARCHAR(30),
    birth_date         DATE,
    conversation_state VARCHAR(30),     -- NULL | AWAITING_NAME | … | REGISTERED
    customer_id        BIGINT REFERENCES customers(id) ON DELETE SET NULL,
    is_active          BOOLEAN DEFAULT true,
    is_blocked         BOOLEAN DEFAULT false,
    subscribed_at      TIMESTAMP DEFAULT NOW(),
    last_interaction_at TIMESTAMP,
    created_at         TIMESTAMP DEFAULT NOW(),
    updated_at         TIMESTAMP DEFAULT NOW()
);

-- Delivery addresses collected during DM registration (text-based)
CREATE TABLE instagram_subscriber_addresses (
    id            BIGSERIAL PRIMARY KEY,
    subscriber_id BIGINT NOT NULL REFERENCES instagram_subscribers(id) ON DELETE CASCADE,
    address       VARCHAR(500) NOT NULL,
    is_default    BOOLEAN DEFAULT false,
    created_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_ig_subscriber_igsid     ON instagram_subscribers(igsid);
CREATE INDEX idx_ig_sub_addr_subscriber  ON instagram_subscriber_addresses(subscriber_id);
