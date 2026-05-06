-- Rebrand seed data from Jangirovs/ElCafe to Qahvoon

-- Update admin user email
UPDATE users SET email = 'admin@qahvoon.uz' WHERE email = 'admin@jangirovs.uz';
UPDATE users SET email = 'operator@qahvoon.uz' WHERE email = 'operator@jangirovs.uz';

-- Update restaurant info
UPDATE restaurants SET name = 'Qahvoon', email = 'info@qahvoon.uz' WHERE email = 'info@jangirovs.uz';

-- Update QR code URLs
UPDATE qr_codes SET url = REPLACE(url, 'jangirovs.uz', 'qahvoon.uz') WHERE url LIKE '%jangirovs.uz%';

-- Update telegram marketing templates
UPDATE telegram_message_templates SET content = REPLACE(content, 'Jangirovs', 'Qahvoon') WHERE content LIKE '%Jangirovs%';
UPDATE telegram_message_templates SET content = REPLACE(content, 'jangirovs.uz', 'qahvoon.uz') WHERE content LIKE '%jangirovs.uz%';
UPDATE telegram_message_templates SET buttons = REPLACE(buttons, 'jangirovs.uz', 'qahvoon.uz') WHERE buttons LIKE '%jangirovs.uz%';

-- Update telegram bot commands
UPDATE telegram_bot_commands SET response = REPLACE(response, 'jangirovs.uz', 'qahvoon.uz') WHERE response LIKE '%jangirovs.uz%';

-- Update SMS templates
UPDATE sms_templates SET content = REPLACE(content, 'Jangirovs', 'Qahvoon') WHERE content LIKE '%Jangirovs%';
