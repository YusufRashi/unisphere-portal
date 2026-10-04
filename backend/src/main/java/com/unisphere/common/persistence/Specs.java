package com.unisphere.common.persistence;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/** Helpers for combining optional query filters. */
public final class Specs {

    private Specs() {
    }

    /** AND-combines the given specifications, ignoring nulls (i.e. filters the client did not supply). */
    @SafeVarargs
    public static <T> Specification<T> allOf(Specification<T>... specs) {
        return Arrays.stream(specs)
                .filter(Objects::nonNull)
                .reduce(Specification::and)
                .orElse((root, query, cb) -> cb.conjunction());
    }

    /**
     * Case-insensitive "contains" pattern for LIKE with the escape character {@code \}. User input is escaped so
     * {@code %} and {@code _} match literally. Returns null for a blank query (no filter).
     */
    public static String containsPattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
