# Flyway Checksum Management Guide

## Overview

Flyway uses checksums to ensure migration scripts haven't been altered after being applied to the database. When a migration file is modified, Flyway will detect a checksum mismatch and fail to start.

## Current Migration Status

**Migrations Applied**: V1 through V16
- V1: Initial schema
- V2: Seed data (admin, operator, restaurant)
- V3: RFM tracking columns
- V4: Courier system
- V5: Consumer OTP authentication
- V6: Enhanced menu management
- V7: Courier location and wallet transactions
- V8: Fix courier location and wallet schema
- V9: Courier tracking in delivery_info
- V10: Kitchen orders table
- V11: Courier status tracking
- V12: Customer addresses
- V13: Customer birthdate and language
- V14: Registration data in OTP codes
- V15: Waiter module tables
- V16: Order lifecycle fields

## Checksum Verification

### Check Current Checksums (PostgreSQL)

```sql
-- View all migrations and their checksums
SELECT
    version,
    description,
    type,
    script,
    checksum,
    installed_on,
    success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### Calculate Checksum for a File

Flyway calculates checksums based on:
1. File content (SQL statements)
2. Line endings
3. Character encoding

To manually calculate a checksum (for reference):
```bash
# Using Flyway CLI
flyway info -configFiles=flyway.conf
```

## Common Checksum Issues

### Issue 1: Migration File Modified After Application

**Error**: `Migration checksum mismatch for migration version X`

**Cause**: A migration file was edited after being applied to the database.

**Solution Options**:

#### Option A: Update Checksum in Database (Quick Fix)
If the change was intentional and you control the database:

```sql
-- Get the current checksum from the file
-- Then update the database record
UPDATE flyway_schema_history
SET checksum = NEW_CHECKSUM_VALUE
WHERE version = 'VERSION_NUMBER';
```

**Example** (V2 seed data update):
```sql
UPDATE flyway_schema_history
SET checksum = 1923741045
WHERE version = '2';
```

#### Option B: Repair with Flyway
```bash
# Recalculates checksums based on current files
flyway repair

# Then run migrations
flyway migrate
```

#### Option C: Create New Migration
Instead of modifying old migrations, create a new one:
```sql
-- V17__update_seed_data.sql
UPDATE users
SET password = '$2b$10$new_hash_here'
WHERE email = 'admin@elcafe.com';
```

### Issue 2: Line Ending Differences

**Cause**: Different line endings between Windows (CRLF) and Unix (LF)

**Solution**:
```bash
# Convert line endings to Unix format
dos2unix src/main/resources/db/migration/*.sql

# Or use Git to normalize
git config --global core.autocrlf input
```

### Issue 3: Baseline Database

If starting with an existing database:

```bash
# Baseline at current version
flyway baseline -baselineVersion=16

# Then migrate new versions
flyway migrate
```

## Best Practices

### 1. Never Modify Applied Migrations
Once a migration is applied to any environment (dev, staging, prod), never modify it.

### 2. Use Repeatable Migrations for Changes
For views, procedures, or functions that might change:
```sql
-- R__create_order_summary_view.sql
CREATE OR REPLACE VIEW order_summary AS ...
```

### 3. Version Control Checksums
Keep a reference of expected checksums in version control:

```yaml
# .flyway/checksums.yml
migrations:
  - version: "1"
    checksum: 1742930421
  - version: "2"
    checksum: 1923741045
  # ... etc
```

### 4. Test Migrations on Fresh Database
Always test new migrations on a fresh database before committing:

```bash
# Drop and recreate database
dropdb elcafe_test
createdb elcafe_test

# Run all migrations
flyway migrate -url=jdbc:postgresql://localhost/elcafe_test
```

## Production Deployment Checklist

### Before Deploying New Migrations:

- [ ] All migration files committed to version control
- [ ] Migrations tested on fresh database
- [ ] Migrations tested on database copy of production
- [ ] Backup of production database created
- [ ] Rollback plan documented
- [ ] No modifications to existing migrations (V1-V16)

### Deployment Steps:

1. **Backup Production Database**
   ```bash
   pg_dump -U postgres elcafe_db > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **Run Flyway Info** (check status)
   ```bash
   flyway info
   ```

3. **Run Flyway Migrate**
   ```bash
   flyway migrate
   ```

4. **Verify Success**
   ```sql
   SELECT version, description, success
   FROM flyway_schema_history
   ORDER BY installed_rank DESC
   LIMIT 5;
   ```

## Troubleshooting

### Error: Checksum Mismatch on Startup

**Spring Boot Application**:
```
Caused by: org.flywaydb.core.api.FlywayException:
Validate failed: Migration checksum mismatch for migration version 2
```

**Quick Fix** (Development Only):
```yaml
# application.yml
spring:
  flyway:
    validate-on-migrate: false  # Disable validation
```

**Proper Fix**:
1. Identify which migration has mismatch
2. Choose repair strategy (update checksum vs new migration)
3. Re-enable validation
4. Test thoroughly

### Error: Already Applied Migration Changed

```sql
-- Find the problematic migration
SELECT * FROM flyway_schema_history
WHERE version = 'X';

-- Option 1: Update checksum (dev only)
UPDATE flyway_schema_history
SET checksum = NEW_CHECKSUM
WHERE version = 'X';

-- Option 2: Delete record and reapply (dangerous!)
-- Only if database can be wiped
DELETE FROM flyway_schema_history WHERE version >= 'X';
-- Then run: flyway migrate
```

## Current Project Status

### Migrations Checksums (As of Latest Commit)

All migrations V1-V16 are applied and have stable checksums. If you encounter a checksum mismatch:

1. **Check if database is up to date**:
   ```sql
   SELECT MAX(version::int) FROM flyway_schema_history;
   -- Should return: 16
   ```

2. **If checksum mismatch on V2** (seed data):
   - Caused by password hash updates
   - Safe to update checksum or create V17 to update passwords

3. **For production databases**:
   - NEVER modify existing migrations
   - NEVER update checksums directly
   - Always create new migrations for changes

## Automated Checksum Management

### Spring Boot Configuration

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    out-of-order: false
    locations: classpath:db/migration
```

### Maven Plugin

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-maven-plugin</artifactId>
    <version>9.22.3</version>
    <configuration>
        <url>jdbc:postgresql://localhost:5432/elcafe_db</url>
        <user>postgres</user>
        <password>postgres</password>
    </configuration>
</plugin>
```

Run commands:
```bash
# Check migration status
mvn flyway:info

# Repair checksums
mvn flyway:repair

# Run migrations
mvn flyway:migrate
```

## Resources

- Flyway Documentation: https://flywaydb.org/documentation/
- Migration Best Practices: https://flywaydb.org/documentation/concepts/migrations
- Checksum Reference: https://flywaydb.org/documentation/concepts/migrations#migration-checksums

## Support

For project-specific issues:
1. Check this guide first
2. Review migration files in `src/main/resources/db/migration/`
3. Check application logs for detailed error messages
4. Create GitHub issue with:
   - Migration version with error
   - Full error message
   - Database state (`flyway_schema_history` contents)
