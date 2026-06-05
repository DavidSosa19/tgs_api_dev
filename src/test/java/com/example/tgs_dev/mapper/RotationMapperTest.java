package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.request.RotationRequest;
import com.example.tgs_dev.controller.response.RotationDTO;
import com.example.tgs_dev.controller.response.RotationEntryDTO;
import com.example.tgs_dev.controller.response.VehicleRotationDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.entity.enums.ShiftDayType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RotationMapper")
class RotationMapperTest {

    RotationMapper sut;

    Route route;
    ScheduleTemplate template;
    Vehicle vehicle;

    @BeforeEach
    void setUp() {
        PersonMapper personMapper = new PersonMapper();
        sut = new RotationMapper(
                new VehicleMapper(personMapper),
                new ScheduleTemplateMapper(new RouteMapper())
        );

        route    = new Route("");
        route.setId(1);
        template = new ScheduleTemplate(route, "T-1", "Morning", 1);
        template.setId(1);
        vehicle  = new Vehicle("V-001", null);
        vehicle.setLicensePlate("ABC-123");
        vehicle.setId(1);
    }

    private VehicleRotation rotation() {
        VehicleRotation r = new VehicleRotation(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                true,
                ShiftDayType.BUSINESS_DAYS
        );
        r.setId(10);
        return r;
    }

    private RotationEntry entry(int pos) {
        RotationEntry e = new RotationEntry();
        e.setId(pos);
        e.setListPosition(pos);
        e.setVehicle(vehicle);
        e.setScheduleTemplate(template);
        return e;
    }

    // ── toRotationDTO ────────────────────────────────────────────────────────

    @Nested @DisplayName("toRotationDTO")
    class ToRotationDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toRotationDTO(null)).isNull();
        }

        @Test @DisplayName("maps all scalar fields")
        void mapsFields() {
            VehicleRotationDTO dto = sut.toRotationDTO(rotation());
            assertThat(dto.id()).isEqualTo(10);
            assertThat(dto.startDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(dto.endDate()).isEqualTo(LocalDate.of(2024, 1, 31));
            assertThat(dto.active()).isTrue();
            assertThat(dto.rotationType()).isEqualTo(ShiftDayType.BUSINESS_DAYS);
        }
    }

    // ── toEntryDTO ───────────────────────────────────────────────────────────

    @Nested @DisplayName("toEntryDTO")
    class ToEntryDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toEntryDTO(null)).isNull();
        }

        @Test @DisplayName("maps id, listPosition and delegates vehicle + template")
        void mapsFields() {
            RotationEntryDTO dto = sut.toEntryDTO(entry(3));
            assertThat(dto.id()).isEqualTo(3);
            assertThat(dto.listPosition()).isEqualTo(3);
            assertThat(dto.vehicle().vehicleNumber()).isEqualTo("V-001");
            assertThat(dto.scheduleTemplate().templateNumber()).isEqualTo("T-1");
        }
    }

    // ── toDTO (rotation + entries) ───────────────────────────────────────────

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("combines rotation header and entry list")
        void combinesRotationAndEntries() {
            RotationDTO dto = sut.toDTO(rotation(), List.of(entry(1), entry(2)));
            assertThat(dto.rotation().id()).isEqualTo(10);
            assertThat(dto.entryList()).hasSize(2);
        }

        @Test @DisplayName("empty entry list → empty entries in DTO")
        void emptyEntries() {
            RotationDTO dto = sut.toDTO(rotation(), List.of());
            assertThat(dto.entryList()).isEmpty();
        }
    }

    // ── toRotationDTOList ────────────────────────────────────────────────────

    @Nested @DisplayName("toRotationDTOList")
    class ToRotationDTOList {

        @Test @DisplayName("empty list → empty list")
        void emptyList() {
            assertThat(sut.toRotationDTOList(List.of())).isEmpty();
        }

        @Test @DisplayName("maps every element preserving order")
        void mapsAll() {
            VehicleRotation r2 = new VehicleRotation(
                    LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 28), true, ShiftDayType.HOLIDAYS);
            r2.setId(20);
            List<VehicleRotationDTO> dtos = sut.toRotationDTOList(List.of(rotation(), r2));
            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).id()).isEqualTo(10);
            assertThat(dtos.get(1).id()).isEqualTo(20);
        }
    }

    // ── toEntity ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("toEntity")
    class ToEntity {

        @Test @DisplayName("maps fields; null active defaults to TRUE via requireNonNullElse")
        void nullActive_defaultsToTrue() {
            var req = new RotationRequest(
                    LocalDate.of(2024, 3, 1),
                    LocalDate.of(2024, 3, 31),
                    null,
                    ShiftDayType.BUSINESS_DAYS,
                    List.of()
            );
            VehicleRotation r = sut.toEntity(req);
            assertThat(r.getActive()).isTrue();
            assertThat(r.getRotationType()).isEqualTo(ShiftDayType.BUSINESS_DAYS);
        }

        @Test @DisplayName("applies explicit active=false")
        void explicitActiveFalse() {
            var req = new RotationRequest(
                    LocalDate.of(2024, 4, 1),
                    LocalDate.of(2024, 4, 30),
                    false,
                    ShiftDayType.HOLIDAYS,
                    List.of()
            );
            VehicleRotation r = sut.toEntity(req);
            assertThat(r.getActive()).isFalse();
        }
    }

    // ── updateEntity ─────────────────────────────────────────────────────────

    @Nested @DisplayName("updateEntity")
    class UpdateEntity {

        @Test @DisplayName("overwrites start/end dates and rotationType")
        void updatesFields() {
            VehicleRotation r = rotation();
            var req = new RotationRequest(
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 1, 31),
                    null,
                    ShiftDayType.HOLIDAYS,
                    List.of()
            );
            sut.updateEntity(r, req);
            assertThat(r.getStartDate()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(r.getEndDate()).isEqualTo(LocalDate.of(2025, 1, 31));
            assertThat(r.getRotationType()).isEqualTo(ShiftDayType.HOLIDAYS);
        }

        @Test @DisplayName("applies active when not null")
        void updatesActiveWhenPresent() {
            VehicleRotation r = rotation();
            var req = new RotationRequest(
                    r.getStartDate(), r.getEndDate(), false, ShiftDayType.BUSINESS_DAYS, List.of());
            sut.updateEntity(r, req);
            assertThat(r.getActive()).isFalse();
        }

        @Test @DisplayName("skips active when null — leaves existing value")
        void skipsActiveWhenNull() {
            VehicleRotation r = rotation(); // active=true
            var req = new RotationRequest(
                    r.getStartDate(), r.getEndDate(), null, ShiftDayType.BUSINESS_DAYS, List.of());
            sut.updateEntity(r, req);
            assertThat(r.getActive()).isTrue();
        }
    }
}
