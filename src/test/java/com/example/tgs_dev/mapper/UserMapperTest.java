package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.UserDTO;
import com.example.tgs_dev.entity.AppRoleEntity;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserMapper")
class UserMapperTest {

    UserMapper sut;

    @BeforeEach
    void setUp() { sut = new UserMapper(new PersonMapper()); }

    private AppRoleEntity role(String name) {
        AppRoleEntity r = new AppRoleEntity();
        r.setName(name);
        return r;
    }

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("null → null")
        void nullInput_returnsNull() {
            assertThat(sut.toDTO(null)).isNull();
        }

        @Test @DisplayName("mapea id, username, roles, enabled y delega person")
        void mapsAllFields() {
            Person person = new Person("DOC", "John", null, "Doe", null);
            User user = User.builder()
                    .userName("jdoe")
                    .password("secret")
                    .roles(Set.of(role("ADMIN")))
                    .active(true)
                    .person(person)
                    .build();
            user.setId(42L);

            UserDTO dto = sut.toDTO(user);

            assertThat(dto.id()).isEqualTo(42L);
            assertThat(dto.userName()).isEqualTo("jdoe");
            assertThat(dto.roles()).containsExactly("ADMIN");
            assertThat(dto.active()).isTrue();
            assertThat(dto.person()).isNotNull();
            assertThat(dto.person().documentNumber()).isEqualTo("DOC");
        }

        @Test @DisplayName("usuario con múltiples roles → todos los nombres presentes")
        void multipleRoles_allNamesReturned() {
            User user = User.builder()
                    .userName("admin")
                    .password("pw")
                    .roles(Set.of(role("ADMIN"), role("USER")))
                    .active(true)
                    .build();

            UserDTO dto = sut.toDTO(user);

            assertThat(dto.roles()).containsExactlyInAnyOrder("ADMIN", "USER");
        }

        @Test @DisplayName("usuario sin person → person es null en el DTO")
        void nullPerson() {
            User user = User.builder()
                    .userName("admin")
                    .password("pw")
                    .roles(Set.of(role("USER")))
                    .active(true)
                    .build();

            UserDTO dto = sut.toDTO(user);

            assertThat(dto.person()).isNull();
        }

        @Test @DisplayName("usuario sin roles → lista de roles vacía en el DTO")
        void noRoles_emptyList() {
            User user = User.builder()
                    .userName("ghost")
                    .password("pw")
                    .active(true)
                    .build();

            UserDTO dto = sut.toDTO(user);

            assertThat(dto.roles()).isEmpty();
        }
    }
}
