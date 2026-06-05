package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.request.ScheduleTemplateRequest;
import com.example.tgs_dev.controller.response.ScheduleTemplateDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.ScheduleTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScheduleTemplateMapper")
class ScheduleTemplateMapperTest {

    ScheduleTemplateMapper sut;
    Route route;

    @BeforeEach
    void setUp() {
        sut   = new ScheduleTemplateMapper(new RouteMapper());
        route = new Route("R-1");
        route.setId(1);
    }

    // ── toDTO ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toDTO(null)).isNull();
        }

        @Test @DisplayName("maps all fields; null secondaryRoute → null in DTO")
        void mapsAllFields_noSecondaryRoute() {
            ScheduleTemplate t = new ScheduleTemplate(route, "T-01", "Morning", 1);
            t.setId(10);
            t.setActive(true);

            ScheduleTemplateDTO dto = sut.toDTO(t);

            assertThat(dto.id()).isEqualTo(10);
            assertThat(dto.templateNumber()).isEqualTo("T-01");
            assertThat(dto.name()).isEqualTo("Morning");
            assertThat(dto.active()).isTrue();
            assertThat(dto.sequenceOrder()).isEqualTo(1);
            assertThat(dto.route()).isNotNull();
            assertThat(dto.route().routeNumber()).isEqualTo("R-1");
            assertThat(dto.secondaryRoute()).isNull();
        }

        @Test @DisplayName("maps secondaryRoute when present")
        void mapsSecondaryRoute() {
            Route secondary = new Route("R-2");
            secondary.setId(2);
            ScheduleTemplate t = new ScheduleTemplate(route, "T-02", "Evening", 2);
            t.setSecondaryRoute(secondary);

            ScheduleTemplateDTO dto = sut.toDTO(t);

            assertThat(dto.secondaryRoute()).isNotNull();
            assertThat(dto.secondaryRoute().routeNumber()).isEqualTo("R-2");
        }
    }

    // ── toDTOList ────────────────────────────────────────────────────────────

    @Nested @DisplayName("toDTOList")
    class ToDTOList {

        @Test @DisplayName("empty list → empty list")
        void emptyList() {
            assertThat(sut.toDTOList(List.of())).isEmpty();
        }

        @Test @DisplayName("maps every element preserving order")
        void mapsAll() {
            ScheduleTemplate t1 = new ScheduleTemplate(route, "T-A", "A", 1);
            ScheduleTemplate t2 = new ScheduleTemplate(route, "T-B", "B", 2);
            List<ScheduleTemplateDTO> dtos = sut.toDTOList(List.of(t1, t2));
            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).templateNumber()).isEqualTo("T-A");
            assertThat(dtos.get(1).templateNumber()).isEqualTo("T-B");
        }
    }

    // ── updateEntity ─────────────────────────────────────────────────────────

    @Nested @DisplayName("updateEntity")
    class UpdateEntity {

        @Test @DisplayName("overwrites all mutable fields")
        void updatesFields() {
            ScheduleTemplate t = new ScheduleTemplate(route, "OLD", "Old Name", 1);
            Route newRoute = new Route("");
            var req = new ScheduleTemplateRequest(1L, null, "T-NEW", "New Name", 3, null);

            sut.updateEntity(t, req, newRoute, null);

            assertThat(t.getTemplateNumber()).isEqualTo("T-NEW");
            assertThat(t.getName()).isEqualTo("New Name");
            assertThat(t.getSequenceOrder()).isEqualTo(3);
            assertThat(t.getRoute()).isEqualTo(newRoute);
            assertThat(t.getSecondaryRoute()).isNull();
        }

        @Test @DisplayName("applies active when not null")
        void updatesActiveWhenPresent() {
            ScheduleTemplate t = new ScheduleTemplate(route, "T", "N", 1);
            var req = new ScheduleTemplateRequest(1L, null, "T", "N", 1, false);
            sut.updateEntity(t, req, route, null);
            assertThat(t.getActive()).isFalse();
        }

        @Test @DisplayName("skips active when null — leaves existing value")
        void skipsActiveWhenNull() {
            ScheduleTemplate t = new ScheduleTemplate(route, "T", "N", 1);
            t.setActive(true);
            var req = new ScheduleTemplateRequest(1L, null, "T", "N", 1, null);
            sut.updateEntity(t, req, route, null);
            assertThat(t.getActive()).isTrue();
        }
    }
}
