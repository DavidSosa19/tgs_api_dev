package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.VehicleDTO;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VehicleMapper")
class VehicleMapperTest {

    VehicleMapper sut;

    @BeforeEach
    void setUp() { sut = new VehicleMapper(new PersonMapper()); }

    // ── toDTO ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toDTO(null)).isNull();
        }

        @Test @DisplayName("maps fields; null owner → null owner in DTO")
        void mapsFields_noOwner() {
            Vehicle v = new Vehicle("V-100", null);
            v.setId(5);
            v.setLicensePlate("ABC-123");
            v.setActive(true);

            VehicleDTO dto = sut.toDTO(v);

            assertThat(dto.id()).isEqualTo(5);
            assertThat(dto.vehicleNumber()).isEqualTo("V-100");
            assertThat(dto.licensePlate()).isEqualTo("ABC-123");
            assertThat(dto.active()).isTrue();
            assertThat(dto.owner()).isNull();
        }

        @Test @DisplayName("delegates owner to PersonMapper")
        void mapsOwner() {
            Person owner = new Person("ID-1", "Ana", null, "Ruiz", null);
            Vehicle v = new Vehicle("V-200", null);
            v.setOwner(owner);

            VehicleDTO dto = sut.toDTO(v);

            assertThat(dto.owner()).isNotNull();
            assertThat(dto.owner().documentNumber()).isEqualTo("ID-1");
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
            List<VehicleDTO> dtos = sut.toDTOList(
                    List.of(new Vehicle("V-1", (com.example.tgs_dev.entity.Person) null),
                            new Vehicle("V-2", (com.example.tgs_dev.entity.Person) null)));
            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).vehicleNumber()).isEqualTo("V-1");
            assertThat(dtos.get(1).vehicleNumber()).isEqualTo("V-2");
        }
    }
}
