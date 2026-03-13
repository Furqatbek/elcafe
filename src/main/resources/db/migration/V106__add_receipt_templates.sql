CREATE TABLE receipt_templates (
    id                  BIGSERIAL PRIMARY KEY,
    restaurant_id       BIGINT NOT NULL UNIQUE REFERENCES restaurants(id) ON DELETE CASCADE,
    restaurant_name     VARCHAR(100),
    tagline             VARCHAR(200),
    phone               VARCHAR(50),
    website             VARCHAR(100),
    footer_message      VARCHAR(200),
    currency            VARCHAR(10)  NOT NULL DEFAULT 'UZS',
    show_qr_code        BOOLEAN      NOT NULL DEFAULT TRUE,
    qr_url              VARCHAR(500),
    qr_title            VARCHAR(100),
    qr_subtitle         VARCHAR(100),
    paper_width_mm      INTEGER      NOT NULL DEFAULT 58,
    kitchen_header_text VARCHAR(100),
    kitchen_footer_text VARCHAR(100),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_receipt_template_restaurant ON receipt_templates(restaurant_id);
