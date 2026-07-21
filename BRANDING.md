# Branding Configuration Guide

This document explains how to rebrand the entire platform with just a few configuration changes.

## Quick Rebrand (One-Line Changes)

To rebrand the platform, update the branding variables in your `.env` file:

```bash
# Backend branding
BRAND_NAME=Your Brand Name
BRAND_SHORT_NAME=YourBrand
BRAND_TAGLINE=Your Tagline
BRAND_DESCRIPTION=Your Brand Description
BRAND_DOMAIN=yourdomain.com
BRAND_API_URL=https://api.yourdomain.com
BRAND_SUPPORT_EMAIL=support@yourdomain.com
BRAND_TEAM_NAME=Your Team Name

# Frontend branding (must have VITE_ prefix)
VITE_BRAND_NAME=Your Brand Name
VITE_BRAND_SHORT_NAME=YourBrand
VITE_BRAND_TAGLINE=Your Tagline
VITE_BRAND_SUPPORT_EMAIL=support@yourdomain.com
VITE_BRAND_DEMO_EMAIL=admin@yourdomain.com
VITE_BRAND_COLOR_PRIMARY=#your-color
VITE_BRAND_COMPANY_NAME=Your Company Inc.
```

## Configuration Files

### Frontend (`frontend/src/config/branding.js`)

The central branding configuration for the frontend. All values can be overridden via `VITE_BRAND_*` environment variables.

**Supported variables:**
| Variable | Description | Default |
|----------|-------------|---------|
| `VITE_BRAND_NAME` | Primary brand name | Qahvoon |
| `VITE_BRAND_SHORT_NAME` | Short name (no spaces) | Qahvoon |
| `VITE_BRAND_TAGLINE` | Short tagline | Restaurant Delivery |
| `VITE_BRAND_DESCRIPTION` | Full description | Restaurant Delivery Control Service |
| `VITE_BRAND_DOMAIN` | Main domain | qahvoon.uz |
| `VITE_BRAND_API_URL` | Production API URL | https://api.qahvoon.uz |
| `VITE_BRAND_WEBSITE_URL` | Main website URL | https://qahvoon.uz |
| `VITE_BRAND_SUPPORT_EMAIL` | Support email | support@qahvoon.uz |
| `VITE_BRAND_ADMIN_EMAIL` | Admin email | admin@qahvoon.uz |
| `VITE_BRAND_DEMO_EMAIL` | Demo login email | admin@qahvoon.uz |
| `VITE_BRAND_DEMO_PASSWORD` | Demo login password | Admin123! |
| `VITE_BRAND_COLOR_PRIMARY` | Primary brand color | #2563eb |
| `VITE_BRAND_COLOR_PRIMARY_DARK` | Dark variant | #1d4ed8 |
| `VITE_BRAND_COLOR_PRIMARY_LIGHT` | Light variant | #3b82f6 |
| `VITE_BRAND_COLOR_ACCENT` | Accent color | #f59e0b |
| `VITE_BRAND_LOGO_MAIN` | Main logo path | /logo.svg |
| `VITE_BRAND_LOGO_ICON` | Favicon path | /vite.svg |
| `VITE_BRAND_COMPANY_NAME` | Legal company name | Qahvoon Inc. |

### Backend (`src/main/resources/application.yml`)

Backend branding configuration under the `branding:` section. All values can be overridden via `BRAND_*` environment variables.

**Supported variables:**
| Variable | Description | Default |
|----------|-------------|---------|
| `BRAND_NAME` | Primary brand name | Qahvoon |
| `BRAND_SHORT_NAME` | Short name | Qahvoon |
| `BRAND_TAGLINE` | Short tagline | Restaurant Delivery |
| `BRAND_DESCRIPTION` | Full description | Restaurant Delivery Control Service |
| `BRAND_DOMAIN` | Main domain | qahvoon.uz |
| `BRAND_API_URL` | Production API URL | https://api.qahvoon.uz |
| `BRAND_SUPPORT_EMAIL` | Support email | support@qahvoon.uz |
| `BRAND_TEAM_NAME` | Team name for docs | Qahvoon Team |

## Usage in Code

### Frontend (React)

```javascript
import branding from '@/config/branding';

// Use branding values
console.log(branding.name);           // "Qahvoon"
console.log(branding.fullName);       // "Qahvoon - Restaurant Delivery"
console.log(branding.colors.primary); // "#2563eb"
console.log(branding.titles.admin);   // "Qahvoon - Control Panel"

// Or use named exports
import { name, supportEmail, colors } from '@/config/branding';
```

### i18n Translations

Branding is automatically injected into all translation files:

```javascript
import { useTranslation } from 'react-i18next';

const { t } = useTranslation();

// Access branding via translations
t('app.name');              // "Qahvoon - Restaurant Delivery"
t('app.brandName');         // "Qahvoon"
t('branding.supportEmail'); // "support@qahvoon.uz"
t('branding.copyright');    // "© 2024 Qahvoon Inc."
```

### Backend (Spring)

```java
@Value("${branding.name}")
private String brandName;

@Value("${branding.support-email}")
private String supportEmail;
```

## Rebranding Checklist

When rebranding, update the following:

1. **Environment Variables** - Update `.env` file with new brand values
2. **Logo Files** - Replace logo files in `frontend/public/`:
   - `logo.svg` - Main logo
   - `vite.svg` - Favicon
   - `logo-light.svg` - Light version (for dark backgrounds)
   - `logo-dark.svg` - Dark version (for light backgrounds)
3. **Favicon** - Update `frontend/public/favicon.ico`
4. **First admin account** - There is no seeded admin (the old `V2__seed_data.sql` demo seed was removed). The first platform operator is created at boot from the `ADMIN_EMAIL` / `ADMIN_PASSWORD` env vars on an empty database — set those instead.
5. **Documentation** - Update README.md and other docs if needed

## Docker Deployment

When deploying with Docker, set branding via environment variables:

```yaml
# docker-compose.yml
services:
  app:
    environment:
      - BRAND_NAME=Your Brand
      - BRAND_SUPPORT_EMAIL=support@yourbrand.com
      # ... other variables
```

Or use an `.env` file:

```bash
docker-compose --env-file .env up
```

## Development vs Production

- **Development**: Edit `frontend/src/config/branding.js` directly for quick testing
- **Production**: Use environment variables for deployment flexibility

The configuration supports both approaches - hardcoded defaults work for development, while environment variables override them in production.
