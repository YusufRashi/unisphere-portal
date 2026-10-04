package com.unisphere.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unisphere.common.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageableFactoryTest {

    private static final Map<String, String> SORTABLE = Map.of("name", "lastName", "created", "createdAt");
    private static final Sort DEFAULT = Sort.by("id");

    @Test
    void appliesDefaults() {
        Pageable p = PageableFactory.of(null, null, null, SORTABLE, DEFAULT);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(PageableFactory.DEFAULT_SIZE);
        assertThat(p.getSort()).isEqualTo(DEFAULT);
    }

    @Test
    void mapsApiFieldToEntityPropertyWithDirectionAndStableTieBreaker() {
        Pageable p = PageableFactory.of(2, 50, "name,desc", SORTABLE, DEFAULT);
        assertThat(p.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "lastName").and(Sort.by("id")));
    }

    @Test
    void rejectsUnknownSortField() {
        assertThatThrownBy(() -> PageableFactory.of(0, 20, "passwordHash,asc", SORTABLE, DEFAULT))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot sort by 'passwordHash'");
    }

    @Test
    void rejectsOversizedPages() {
        assertThatThrownBy(() -> PageableFactory.of(0, 101, null, SORTABLE, DEFAULT)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PageableFactory.of(-1, 20, null, SORTABLE, DEFAULT)).isInstanceOf(ApiException.class);
    }
}
