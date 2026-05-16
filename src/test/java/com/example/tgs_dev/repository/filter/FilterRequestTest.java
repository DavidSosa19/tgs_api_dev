package com.example.tgs_dev.repository.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FilterRequest")
class FilterRequestTest {

    @Nested @DisplayName("toPageable")
    class ToPageable {

        @Test @DisplayName("default instance → page 0, size 20, sorted by id ASC")
        void defaults_pageZeroSize20SortById() {
            FilterRequest req = new FilterRequest();
            Pageable p = req.toPageable();

            assertThat(p.getPageNumber()).isZero();
            assertThat(p.getPageSize()).isEqualTo(20);
            Sort.Order order = p.getSort().getOrderFor("id");
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test @DisplayName("null sortBy → falls back to 'id'")
        void nullSortBy_fallsBackToId() {
            FilterRequest req = new FilterRequest();
            req.setSortBy(null);

            assertThat(req.toPageable().getSort().getOrderFor("id")).isNotNull();
        }

        @Test @DisplayName("custom sortBy is used in the sort order")
        void customSortBy_usedInSort() {
            FilterRequest req = new FilterRequest();
            req.setSortBy("name");

            assertThat(req.toPageable().getSort().getOrderFor("name")).isNotNull();
            assertThat(req.toPageable().getSort().getOrderFor("id")).isNull();
        }

        @Test @DisplayName("DESC sortDirection → descending sort order")
        void descDirection_producesDescendingOrder() {
            FilterRequest req = new FilterRequest();
            req.setSortDirection("DESC");

            Sort.Order order = req.toPageable().getSort().getOrderFor("id");
            assertThat(order).isNotNull();
            assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test @DisplayName("custom page and size are reflected in the Pageable")
        void customPageAndSize_reflected() {
            FilterRequest req = new FilterRequest();
            req.setPage(3);
            req.setSize(50);

            Pageable p = req.toPageable();
            assertThat(p.getPageNumber()).isEqualTo(3);
            assertThat(p.getPageSize()).isEqualTo(50);
        }
    }
}
