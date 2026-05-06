-- Rebrand seed data from Jangirovs/ElCafe to Qahvoon

-- Update admin user email
UPDATE users SET email = 'admin@qahvoon.uz' WHERE email = 'admin@jangirovs.uz';
UPDATE users SET email = 'operator@qahvoon.uz' WHERE email = 'operator@jangirovs.uz';

-- Update restaurant info
UPDATE restaurants SET name = 'Qahvoon', email = 'info@qahvoon.uz' WHERE email = 'info@jangirovs.uz';

-- Update QR code URLs
UPDATE qr_codes SET short_url = REPLACE(short_url, 'jangirovs.uz', 'qahvoon.uz') WHERE short_url LIKE '%jangirovs.uz%';

-- Update telegram marketing templates
UPDATE telegram_templates SET content = REPLACE(content, 'Jangirovs', 'Qahvoon') WHERE content LIKE '%Jangirovs%';
UPDATE telegram_templates SET content = REPLACE(content, 'jangirovs.uz', 'qahvoon.uz') WHERE content LIKE '%jangirovs.uz%';
UPDATE telegram_templates SET buttons_config = REPLACE(buttons_config::text, 'jangirovs.uz', 'qahvoon.uz')::jsonb WHERE buttons_config::text LIKE '%jangirovs.uz%';

-- Update telegram bot commands
UPDATE telegram_bot_commands SET response = REPLACE(response, 'jangirovs.uz', 'qahvoon.uz') WHERE response LIKE '%jangirovs.uz%';

-- Update SMS templates
UPDATE sms_templates SET content = REPLACE(content, 'Jangirovs', 'Qahvoon') WHERE content LIKE '%Jangirovs%';
