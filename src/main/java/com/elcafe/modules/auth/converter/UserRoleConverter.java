package com.elcafe.modules.auth.converter;

import com.elcafe.modules.auth.enums.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists {@link UserRole} as its name (identical on-disk representation to
 * {@code @Enumerated(EnumType.STRING)}), but on read maps any value this enum
 * does not recognise to {@link UserRole#UNKNOWN} instead of throwing.
 *
 * <p>Without this, a stray role string in the {@code users} table (for
 * example a {@code SUPER_ADMIN} row seeded before the constant existed) makes
 * Hibernate throw {@code IllegalArgumentException: No enum constant …} during
 * entity hydration. That bubbles up as an {@code InternalAuthenticationServiceException}
 * — a 500 — and blocks that user from logging in at all. Mapping to UNKNOWN
 * turns it into a clean "disabled account" outcome (UNKNOWN carries no
 * authorities and is treated as not-enabled) while logging the offending
 * value so the data can be corrected.
 *
 * <p>Applied explicitly via {@code @Convert}; not {@code autoApply} so it only
 * governs the {@code User.role} column.
 */
@Slf4j
@Converter
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole role) {
        if (role == null) {
            return null;
        }
        // UNKNOWN is a read-only, in-memory sentinel produced when a DB value
        // has no matching constant. It must never be written back: doing so
        // would overwrite (and permanently destroy) the original unrecognised
        // role string on the next dirty flush. Fail loudly instead.
        if (role == UserRole.UNKNOWN) {
            throw new IllegalStateException(
                    "Refusing to persist UserRole.UNKNOWN — this account's stored role is unrecognised "
                            + "and must be corrected directly in the database, not overwritten with the sentinel.");
        }
        return role.name();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        try {
            return UserRole.valueOf(dbValue);
        } catch (IllegalArgumentException e) {
            log.error("Unrecognised user role '{}' in database — resolving to UNKNOWN. "
                    + "This account will be treated as disabled until the role is corrected.", dbValue);
            return UserRole.UNKNOWN;
        }
    }
}
