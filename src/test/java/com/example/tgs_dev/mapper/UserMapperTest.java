package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.UserDTO;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.entity.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserMapper")
class UserMapperTest {

    UserMapper sut;

    @BeforeEach
    void setUp() { sut = new UserMapper(new PersonMapper()); }

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toDTO(null)).isNull();
        }

        @Test @DisplayName("maps id, username, role name, enabled flag and delegates person")
        void mapsAllFields() {
            Person person = new Person("DOC", "John", null, "Doe", null);
            User user = User.builder()
                    .userName("jdoe")
                    .password("secret")
                    .rol(Role.ADMIN)
                    .active(true)
                    .person(person)
                    .build();
            user.setId(42L);

            UserDTO dto = sut.toDTO(user);

            assertThat(dto.id()).isEqualTo(42L);
            assertThat(dto.userName()).isEqualTo("jdoe");
            assertThat(dto.rol()).isEqualTo("ADMIN");
            assertThat(dto.active()).isTrue();
            assertThat(dto.person()).isNotNull();
            assertThat(dto.person().documentNumber()).isEqualTo("DOC");
        }

        @Test @DisplayName("user without person → person field is null in DTO")
        void nullPerson() {
            User user = User.builder()
                    .userName("admin")
                    .password("pw")
                    .rol(Role.USER)
                    .active(true)
                    .build();

            UserDTO dto = sut.toDTO(user);

            assertThat(dto.person()).isNull();
        }
    }
}
