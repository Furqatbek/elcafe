package com.elcafe.common.web;

import com.elcafe.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Defends list endpoints against a user passing arbitrary entity field
 * names into a {@code sortBy} request parameter. Spring Data's
 * {@code Sort.by(String)} accepts any JPA-mapped property — that's not
 * SQL injection (Hibernate resolves it against the metamodel), but it
 * does mean a caller could sort users by passwordHash and learn the
 * ordering, or trigger an expensive sort on an unindexed text column.
 *
 * Usage:
 * <pre>
 *   Sort sort = SortFieldWhitelist.sort(sortBy, sortDir,
 *           "id", "name", "createdAt");
 * </pre>
 */
public final class SortFieldWhitelist {

    private SortFieldWhitelist() {}

    /**
     * Returns a {@link Sort} sorted by the given field if it appears in
     * {@code allowed}, throwing {@link BadRequestException} otherwise.
     * The first entry of {@code allowed} is used as the fallback when
     * {@code field} is null or blank, keeping the existing
     * {@code defaultValue} behaviour intact at the call site.
     */
    public static Sort sort(String field, String direction, String... allowed) {
        if (allowed.length == 0) {
            throw new IllegalArgumentException("allowed fields must not be empty");
        }
        Set<String> allowedSet = new LinkedHashSet<>(Arrays.asList(allowed));
        String resolved = (field == null || field.isBlank()) ? allowed[0] : field;
        if (!allowedSet.contains(resolved)) {
            throw new BadRequestException(
                    "Invalid sort field '" + resolved + "'. Allowed: " + allowedSet);
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(dir, resolved);
    }
}
