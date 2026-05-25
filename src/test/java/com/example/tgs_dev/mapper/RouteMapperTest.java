package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.request.RouteRequest;
import com.example.tgs_dev.controller.response.RouteDTO;
import com.example.tgs_dev.entity.Route;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteMapper")
class RouteMapperTest {

    RouteMapper sut;

    @BeforeEach
    void setUp() { sut = new RouteMapper(); }

    // ── toDTO ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toDTO(null)).isNull();
        }

        @Test @DisplayName("maps all fields")
        void mapsAllFields() {
            Route r = new Route("R-01");
            r.setId(3);
            r.setActive(true);

            RouteDTO dto = sut.toDTO(r);

            assertThat(dto.id()).isEqualTo(3);
            assertThat(dto.routeNumber()).isEqualTo("R-01");
            assertThat(dto.active()).isTrue();
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
            List<RouteDTO> dtos = sut.toDTOList(
                    List.of(new Route("1"), new Route("2")));
            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).routeNumber()).isEqualTo("1");
            assertThat(dtos.get(1).routeNumber()).isEqualTo("2");
        }
    }

    // ── toEntity ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("toEntity")
    class ToEntity {

        @Test @DisplayName("maps routeNumber from request")
        void mapsFields() {
            var req = new RouteRequest("R-99");
            Route r = sut.toEntity(req);
            assertThat(r.getRouteNumber()).isEqualTo("R-99");
        }
    }

    // ── updateEntity ─────────────────────────────────────────────────────────

    @Nested @DisplayName("updateEntity")
    class UpdateEntity {

        @Test @DisplayName("overwrites routeNumber")
        void updatesFields() {
            Route r = new Route("OLD");
            sut.updateEntity(r, new RouteRequest("NEW"));
            assertThat(r.getRouteNumber()).isEqualTo("NEW");
        }
    }
}
