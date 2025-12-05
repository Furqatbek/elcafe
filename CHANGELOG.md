# Changelog

All notable changes to the El Cafe Restaurant Delivery Control Service will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added - 2025-11-24

#### Analytics System (Phase 2)

**Backend**:
- Created comprehensive analytics module with 19 metrics across 4 categories:

**Financial Analytics (6 metrics)**:
  - Daily revenue with payment method breakdown (cash, card, online)
  - Average Order Value (AOV) calculation
  - Sales per category with revenue percentages
  - Cost of Goods Sold (COGS) tracking
  - Food cost percentage calculation
  - Gross profit margin analysis

**Profitability Analytics**:
  - Labor cost tracking and percentage
  - COGS + Labor cost (crucial profitability metric)
  - Net profit margin calculation
  - Contribution margin per menu item
  - Top/bottom performing products by contribution

**Operational Analytics (9 metrics)**:
  - Sales per hour with hourly breakdown
  - Peak hours detection and analysis
  - Table turnover rate calculation
  - Order preparation time tracking (min/max/avg/median)
  - Dine-in wait time analysis
  - Delivery time tracking and optimization
  - Percentage of orders meeting targets (15min prep, 30min delivery)
  - Total orders analyzed

**Customer Analytics (3 metrics)**:
  - Customer retention rate and churn rate
  - Customer Lifetime Value (CLV) calculation
  - One-time vs repeat customer analysis
  - Customer satisfaction score aggregation (Google, Yandex, Telegram, internal)

**Inventory Analytics (1 metric)**:
  - Inventory turnover ratio
  - Days to sell inventory calculation
  - Ingredient-level turnover tracking
  - Cost value analysis per ingredient

**Implementation Details**:
- Created 13 comprehensive DTOs for analytics responses
- Implemented 4 specialized analytics services:
  - `FinancialAnalyticsService` - Revenue, COGS, margins, profitability
  - `OperationalAnalyticsService` - Timing, peak hours, turnover
  - `CustomerAnalyticsService` - Retention, LTV, satisfaction
  - `InventoryAnalyticsService` - Turnover calculations
- Created `AnalyticsSummaryService` for dashboard overview
- Implemented `AnalyticsController` with 15 REST endpoints:
  - `GET /api/v1/analytics/summary` - Dashboard with all key metrics
  - `GET /api/v1/analytics/financial/daily-revenue`
  - `GET /api/v1/analytics/financial/sales-by-category`
  - `GET /api/v1/analytics/financial/cogs`
  - `GET /api/v1/analytics/financial/profitability`
  - `GET /api/v1/analytics/financial/contribution-margins`
  - `GET /api/v1/analytics/operational/sales-per-hour`
  - `GET /api/v1/analytics/operational/peak-hours`
  - `GET /api/v1/analytics/operational/table-turnover`
  - `GET /api/v1/analytics/operational/order-timing`
  - `GET /api/v1/analytics/customer/retention`
  - `GET /api/v1/analytics/customer/ltv`
  - `GET /api/v1/analytics/customer/satisfaction`
  - `GET /api/v1/analytics/inventory/turnover`
- All endpoints secured with role-based access (ADMIN, OPERATOR)
- Date range filtering on all analytics
- Optional restaurant-specific filtering
- Automatic calculation from existing order data

#### Courier Management System

**Backend**:
- Added `COURIER` role to UserRole enum
- Created comprehensive courier domain model:
  - `CourierProfile` entity with user relationship, vehicle info, license, availability
  - `CourierWallet` entity for balance and transaction tracking
  - `WalletTransaction` entity for transaction history
  - `CourierTariff` entity for bonus/fine tariff rules
  - `CourierBonusFine` entity for applied bonuses/fines
  - `CourierAttendance` entity for daily attendance tracking
- Created enums: `CourierType`, `CourierVehicle`, `TariffType`, `TransactionType`
- Created DTOs: `CourierDTO`, `CreateCourierRequest`, `UpdateCourierRequest`, `CourierWalletDTO`, `TariffDTO`
- Implemented `CourierService` with full CRUD operations and wallet management
- Created `CourierController` with paginated REST endpoints:
  - `GET /api/v1/couriers` - List all couriers with pagination
  - `GET /api/v1/couriers/{id}` - Get courier details
  - `GET /api/v1/couriers/{id}/wallet` - Get courier wallet balance
  - `POST /api/v1/couriers` - Create new courier
  - `PUT /api/v1/couriers/{id}` - Update courier
  - `DELETE /api/v1/couriers/{id}` - Delete courier
- Created Flyway migration V4 with comprehensive database schema
- Automatic wallet creation on courier registration

**Frontend**:
- Created Couriers page with Excel-style table and 10 columns
- Implemented full pagination (5/10/20/50 per page)
- Added multi-dimensional filtering:
  - Search by name, email, phone, city
  - Filter by status (Active/Inactive)
  - Filter by courier type (Full Time, Part Time, Freelance, Contractor)
  - Filter by vehicle (Bicycle, Motorcycle, Scooter, Car, On Foot)
- Created comprehensive CRUD modals:
  - Create/edit form with validation
  - Password visibility toggle
  - Vehicle and courier type selectors
  - Address and emergency contact fields
- Added wallet modal displaying:
  - Current balance
  - Total earned
  - Total bonuses
  - Total fines
  - Total withdrawn
- Implemented CSV export with all courier data
- Added to Employees sub-menu in navigation
- Added complete i18n support (EN, RU, UZ)

**Features**:
- Courier profiles linked to User entity with COURIER role
- Wallet system with automatic balance tracking
- Support for bonuses and fines (foundation for future tariff system)
- Multiple courier types and vehicle options
- Availability and verification status tracking
- Emergency contact management
- Admin-only access to courier management

#### Operator Management System

**Backend**:
- Created operator-specific DTOs and service layer
- Implemented `OperatorController` with pagination
- Added `GET /api/v1/operators` endpoint with sorting and pagination

**Frontend**:
- Created Operators page with Excel-style table
- Added operator CRUD operations with modals
- Implemented pagination and filtering
- Added to Employees sub-menu
- Full i18n support (EN, RU, UZ)

#### SMS Integration (Eskiz.uz)

**Backend**:
- Integrated with Eskiz.uz SMS broker API
- Created comprehensive SMS module with configuration, DTOs, service, and controller
- Implemented `SmsProperties` configuration class with environment variable support
- Created enums: `MessageStatus`, `DispatchStatus`
- Created DTOs for all API operations:
  - Authentication: `AuthRequest`, `AuthResponse`
  - Sending: `SendSmsRequest`, `SendBatchSmsRequest`, `SendGlobalSmsRequest`, `SendSmsResponse`
  - Status: `MessageStatusResponse`, `DispatchStatusResponse`
  - User info: `UserInfoResponse`, `UserLimitResponse`
  - Templates: `TemplateResponse`
  - Messages: `UserMessagesResponse`
- Implemented `SmsService` with automatic token management and all available methods:
  - `authenticate()` - Login to SMS broker
  - `refreshToken()` - Refresh authentication token
  - `getUserInfo()` - Get user account information
  - `getUserLimit()` - Get SMS limit and count
  - `sendSms()` - Send single SMS
  - `sendBatchSms()` - Send batch SMS
  - `sendGlobalSms()` - Send global SMS to multiple recipients
  - `getMessageStatus()` - Get message delivery status
  - `getUserMessages()` - Get user messages with pagination
  - `getUserMessagesByDispatch()` - Get messages by dispatch ID
  - `getDispatchStatus()` - Get dispatch status and statistics
  - `getTemplates()` - Get SMS templates
- Created `SmsController` with 12 REST endpoints:
  - `POST /api/v1/sms/auth/login` - Authenticate with SMS broker
  - `PATCH /api/v1/sms/auth/refresh` - Refresh token
  - `GET /api/v1/sms/auth/user` - Get user info
  - `GET /api/v1/sms/user/limit` - Get user limit
  - `GET /api/v1/sms/templates` - Get templates
  - `POST /api/v1/sms/send` - Send single SMS
  - `POST /api/v1/sms/send-batch` - Send batch SMS
  - `POST /api/v1/sms/send-global` - Send global SMS
  - `GET /api/v1/sms/message/{id}/status` - Get message status
  - `GET /api/v1/sms/messages` - Get user messages (paginated)
  - `GET /api/v1/sms/dispatch/{dispatchId}/messages` - Get dispatch messages
  - `GET /api/v1/sms/dispatch/{dispatchId}/status` - Get dispatch status
- Added configuration to `application.yml` with environment variables
- Implemented automatic token refresh (29-day expiration)
- Mock mode support for testing without sending real SMS

**Features**:
- Complete integration with Eskiz.uz SMS API
- Automatic authentication and token management
- Support for single, batch, and global SMS sending
- Message status tracking and delivery reports
- Dispatch management for bulk campaigns
- SMS templates support
- User limit monitoring
- Mock mode for development/testing
- Admin and Operator role access control
- Comprehensive error handling and logging
- Configurable timeouts and retry logic

**Configuration**:
Environment variables:
- `ESKIZ_SMS_EMAIL` - SMS broker account email
- `ESKIZ_SMS_PASSWORD` - SMS broker account password
- `ESKIZ_SMS_ENABLED` - Enable/disable SMS sending (default: true)
- `ESKIZ_SMS_BASE_URL` - API base URL (default: https://notify.eskiz.uz/api)
- `ESKIZ_SMS_CALLBACK_URL` - Callback URL for delivery reports

#### Consumer OTP Authentication System

**Backend**:
- Implemented phone-based OTP authentication for consumers (mobile app/website users)
- Created database migration V5 for OTP and session management
- Created entities:
  - `OtpCode` - Stores 6-digit OTP codes with expiration and verification tracking
  - `ConsumerSession` - Manages JWT-based consumer sessions with refresh tokens
- Created repositories with custom queries:
  - `OtpCodeRepository` - OTP management with rate limiting and cleanup
  - `ConsumerSessionRepository` - Session management and invalidation
- Created DTOs:
  - `ConsumerLoginRequest` - Phone number input for OTP request
  - `ConsumerLoginResponse` - OTP sent confirmation with expiration
  - `VerifyOtpRequest` - OTP verification input
  - `ConsumerAuthResponse` - Authentication tokens and session info
  - `RefreshTokenRequest` - Token refresh input (reused from existing)
- Implemented `ConsumerAuthService` with comprehensive features:
  - `requestOtp()` - Generate and send 6-digit OTP via SMS
  - `verifyOtp()` - Verify OTP and create session
  - `refreshAccessToken()` - Refresh expired access tokens
  - `logout()` - Invalidate consumer session
  - `cleanupExpiredData()` - Scheduled cleanup task (hourly)
- Created `ConsumerAuthController` with 4 REST endpoints:
  - `POST /api/v1/consumer/auth/login` - Request OTP
  - `POST /api/v1/consumer/auth/verify` - Verify OTP and get tokens
  - `POST /api/v1/consumer/auth/refresh` - Refresh access token
  - `POST /api/v1/consumer/auth/logout` - Logout and invalidate session
- Updated `SecurityConfig` to allow public access to consumer auth endpoints
- Added `@EnableScheduling` to main application for cleanup tasks
- Integrated with SMS service for OTP delivery

**Features**:
- Phone-only authentication (no password required)
- 6-digit OTP codes with 5-minute expiration
- Rate limiting: 3 OTP requests per minute per phone number
- Maximum 3 verification attempts per OTP
- JWT access tokens (1 hour expiration)
- Refresh tokens (30 days expiration)
- Automatic customer creation on first login
- Session tracking with IP address and user agent
- Automatic invalidation of old sessions on new login
- Scheduled cleanup of expired OTPs and sessions (runs hourly)
- Development mode: Include OTP in response (configurable)
- Comprehensive error handling and validation
- Phone number normalization
- Security features:
  - OTP attempt limiting
  - Rate limiting per phone number
  - Session invalidation support
  - Token-based authentication

**Database Schema**:
- `otp_codes` table with indexes on phone_number, expires_at, is_verified
- `consumer_sessions` table with indexes on tokens and expiration
- Foreign key to customers table for linked accounts

**Configuration**:
Environment variables:
- `CONSUMER_OTP_INCLUDE_IN_RESPONSE` - Include OTP in response for testing (default: false)

Configuration properties:
- `app.consumer.otp.expiration-minutes` - OTP validity (default: 5)
- `app.consumer.otp.max-attempts` - Max verification attempts (default: 3)
- `app.consumer.otp.rate-limit-minutes` - Rate limit window (default: 1)
- `app.consumer.otp.rate-limit-count` - Max requests in window (default: 3)
- `app.consumer.session.access-token-expiration` - Access token TTL (default: 1 hour)
- `app.consumer.session.refresh-token-expiration` - Refresh token TTL (default: 30 days)

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
