package com.unisphere.common.web;

import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds a {@link Pageable} from raw query parameters. Sort fields are whitelisted and mapped from
 * API names to entity properties, so clients cannot sort on arbitrary (or sensitive) columns.
 */
public final class PageableFactory {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageableFactory() {
    }

    /**
     * @param sort          e.g. {@code "lastName,asc"}; may be null
     * @param sortableFields API field name → entity property path
     * @param defaultSort   used when {@code sort} is blank
     */
    public static Pageable of(Integer page, Integer size, String sort, Map<String, String> sortableFields, Sort defaultSort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "page must be 0 or greater.");
        }
        if (s < 1 || s > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "size must be between 1 and " + MAX_SIZE + ".");
        }
        return PageRequest.of(p, s, parseSort(sort, sortableFields, defaultSort));
    }

    private static Sort parseSort(String sort, Map<String, String> sortableFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String property = sortableFields.get(parts[0].trim());
        if (property == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Cannot sort by '%s'. Allowed: %s.".formatted(parts[0].trim(), String.join(", ", sortableFields.keySet())));
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().toLowerCase(Locale.ROOT).equals("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        // Secondary sort on id keeps pagination stable when the primary key has ties.
        return Sort.by(direction, property).and(Sort.by("id"));
    }
}
