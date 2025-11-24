# Changelog

All notable changes to the El Cafe Restaurant Delivery Control Service will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added - 2025-11-24

#### RFM Customer Activity Tracking System

**Backend**:
- Added `RegistrationSource` enum for tracking customer registration channels (Telegram Bot, Website, Admin Panel, Mobile App, Phone Call, Walk-in, Other)
- Added `OrderSource` enum for tracking order placement channels
- Added `registration_source` field to Customer entity with database migration (V3)
- Added `order_source` field to Order entity with database migration (V3)
- Created `CustomerActivityDTO` with comprehensive RFM metrics:
  - Recency (days since last order)
  - Frequency (total number of orders)
  - Monetary (total amount spent)
  - Average check value
  - RFM scores (1-5 quintile-based scoring)
  - Customer segment classification (11 segments)
  - Order sources tracking
  - Registration information
- Created `CustomerActivityFilterDTO` for advanced filtering capabilities
- Implemented `CustomerActivityService` with:
  - RFM calculation algorithm using quintile-based scoring
  - 11-segment customer classification:
    - Champions (high R, F, M)
    - Loyal Customers (high frequency)
    - Potential Loyalists (recent with decent frequency)
    - Recent Customers (high recency, low frequency)
    - At Risk (low recency but valuable)
    - Can't Lose Them (lost high-value customers)
    - Plus 5 additional segments
  - Multi-dimensional filtering support
- Added `CustomerActivityController` at `/api/v1/customers/activity` with endpoints:
  - `GET /api/v1/customers/activity` - Get all customers with RFM analysis
  - `GET /api/v1/customers/activity/filter` - Filter with query parameters
  - `POST /api/v1/customers/activity/filter` - Complex filtering with request body
- Extended `OrderRepository` with activity tracking queries:
  - Count orders by customer
  - Sum total amount by customer
  - Find distinct order sources by customer
  - Get last order date by customer

**Frontend**:
- Created CustomerSegments page (formerly Customers page) with:
  - Excel-style table with 11 columns (ID, Name, Phone, Avg Check, Total Amount, Recency, Frequency, Orders, Sources, Registration Date, Segment)
  - Advanced filter system with 10+ parameters:
    - Search (name, email, phone, city)
    - Status filter (Active/Inactive)
    - Registration source filter
    - Date range filter (start/end dates)
    - RFM filters (min/max for recency, frequency, monetary value)
  - Color-coded recency badges:
    - Green: ≤7 days (recent activity)
    - Blue: ≤30 days (active)
    - Yellow: ≤90 days (declining)
    - Red: >90 days (at risk)
  - Color-coded segment badges for all 11 customer segments
  - CSV export with complete RFM metrics
  - New customer creation with registration source selector
- Created new Customers page with:
  - Simple personal data focus (9 columns: ID, First Name, Last Name, Email, Phone, City, Tags, Status, Created At)
  - Basic search and status filtering
  - CSV export for personal information
  - New customer creation modal
  - Clean, professional Excel-style layout
- Updated navigation in Layout component:
  - Added "Clients" expandable menu item
  - Added sub-menu items:
    - "Customers" → `/customers` (personal data)
    - "Customer Segments" → `/customer-segments` (RFM analytics)
- Updated routing in App.jsx to support both customer pages
- Added API service methods for customer activity endpoints
- Added complete i18n translations in three languages (English, Russian, Uzbek):
  - Navigation labels for Clients sub-menu
  - CustomerSegments section with title, descriptions
  - Customer section with RFM terminology:
    - `averageCheck`, `totalAmount`, `recency`, `frequency`, `monetary`
    - `orderSources`, `registrationSource`, `segment`
    - `rfmFilters` and filter labels
    - All registration and order source options
    - Days/orders units and time descriptions

**Database Migrations**:
- `V3__add_rfm_tracking_columns.sql`:
  - Added `registration_source` column to customers table (VARCHAR(50), default: 'ADMIN_PANEL')
  - Added `order_source` column to orders table (VARCHAR(50), default: 'ADMIN_PANEL')
  - Added PostgreSQL comments for documentation

### Changed - 2025-11-24

- Restructured customer management pages:
  - Renamed original Customers page to CustomerSegments for RFM analytics
  - Created new lightweight Customers page for personal data display
  - Both pages now accessible via Clients sub-menu in navigation
- Updated navigation structure to use expandable Clients menu with two sub-items
- Enhanced customer data model with registration and order source tracking
- Improved customer filtering capabilities with RFM-based filters

### Fixed - 2025-11-24

- Fixed database schema validation error by adding Flyway migration V3 for new RFM tracking columns
- Fixed API routing conflict by correcting CustomerActivityController path to include `/v1` prefix (was `/api/customers/activity`, now `/api/v1/customers/activity`)

## Previous Releases

### [1.0.0] - 2025-01-23

Initial production-ready release with complete restaurant delivery control system.

**Features**:
- JWT-based authentication and authorization
- Restaurant management with CRUD operations
- Menu management with categories, products, variants, and add-ons
- Order management with full lifecycle tracking
- Courier integration with webhook support
- Customer relationship management (CRM)
- Redis caching for menu data
- OpenAPI/Swagger documentation
- Docker containerization
- PostgreSQL database with Flyway migrations
- React frontend with Shadcn UI
- Multi-language support (English, Russian, Uzbek)
- Professional CRM/ERP-style sidebar navigation

---

## Legend

- **Added**: New features
- **Changed**: Changes in existing functionality
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security improvements
