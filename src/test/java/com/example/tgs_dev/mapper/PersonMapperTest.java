package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.request.PersonRequest;
import com.example.tgs_dev.controller.response.PersonDTO;
import com.example.tgs_dev.entity.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersonMapper")
class PersonMapperTest {

    PersonMapper sut;

    @BeforeEach
    void setUp() { sut = new PersonMapper(); }

    // ── toDTO ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toDTO(null)).isNull();
        }

        @Test @DisplayName("maps all fields")
        void mapsAllFields() {
            Person p = new Person("DOC-1", "John", "M", "Doe", "Smith");
            p.setId(7);
            p.setActive(true);

            PersonDTO dto = sut.toDTO(p);

            assertThat(dto.id()).isEqualTo(7);
            assertThat(dto.documentNumber()).isEqualTo("DOC-1");
            assertThat(dto.firstName()).isEqualTo("John");
            assertThat(dto.secondName()).isEqualTo("M");
            assertThat(dto.firstLastName()).isEqualTo("Doe");
            assertThat(dto.secondLastName()).isEqualTo("Smith");
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

        @Test @DisplayName("maps every element in order")
        void mapsEachElement() {
            Person p1 = new Person("A", "Ana", null, "Ruiz", null);
            Person p2 = new Person("B", "Bob", null, "Park", null);
            List<PersonDTO> dtos = sut.toDTOList(List.of(p1, p2));
            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).documentNumber()).isEqualTo("A");
            assertThat(dtos.get(1).documentNumber()).isEqualTo("B");
        }
    }

    // ── toEntity ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("toEntity")
    class ToEntity {

        @Test @DisplayName("maps required fields; active defaults to entity default when request.active is null")
        void requiredFields_activeNull() {
            var req = new PersonRequest("DOC", "Jane", null, "Doe", null, null);
            Person p = sut.toEntity(req);
            assertThat(p.getDocumentNumber()).isEqualTo("DOC");
            assertThat(p.getFirstName()).isEqualTo("Jane");
            assertThat(p.getFirstLastName()).isEqualTo("Doe");
            // entity default is true; request.active == null so setActive not called
            assertThat(p.getActive()).isTrue();
        }

        @Test @DisplayName("applies request.active when provided")
        void setsActiveWhenPresent() {
            var req = new PersonRequest("DOC", "Jane", null, "Doe", null, false);
            Person p = sut.toEntity(req);
            assertThat(p.getActive()).isFalse();
        }
    }

    // ── updateEntity ─────────────────────────────────────────────────────────

    @Nested @DisplayName("updateEntity")
    class UpdateEntity {

        @Test @DisplayName("overwrites all mutable fields")
        void updatesFields() {
            Person p = new Person("OLD", "Old", null, "Last", null);
            var req = new PersonRequest("NEW", "New", "Mid", "Last2", "Suffix", null);
            sut.updateEntity(p, req);
            assertThat(p.getDocumentNumber()).isEqualTo("NEW");
            assertThat(p.getFirstName()).isEqualTo("New");
            assertThat(p.getSecondName()).isEqualTo("Mid");
            assertThat(p.getFirstLastName()).isEqualTo("Last2");
            assertThat(p.getSecondLastName()).isEqualTo("Suffix");
        }

        @Test @DisplayName("applies active when not null")
        void updatesActiveWhenPresent() {
            Person p = new Person("D", "F", null, "L", null);
            sut.updateEntity(p, new PersonRequest("D", "F", null, "L", null, false));
            assertThat(p.getActive()).isFalse();
        }

        @Test @DisplayName("skips active when null — leaves existing value")
        void skipsActiveWhenNull() {
            Person p = new Person("D", "F", null, "L", null);
            p.setActive(true);
            sut.updateEntity(p, new PersonRequest("D", "F", null, "L", null, null));
            assertThat(p.getActive()).isTrue();
        }
    }
}
