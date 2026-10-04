package com.unisphere.common.persistence;

import java.util.Arrays;
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
}
