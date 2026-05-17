package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.CompanyContextDTO;
import com.example.tgs_dev.controller.response.UserContextDTO;
import com.example.tgs_dev.entity.AppRoleEntity;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.PermissionEntity;
import com.example.tgs_dev.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserContextMapper")
class UserContextMapperTest {

    private final UserContextMapper sut = new UserContextMapper();

    // ── helpers ───────────────────────────────────────────────────────────────

    private Company company(int id) {
        Company c = new Company("Empresa Test", "900-1");
        c.setId(id);
        return c;
    }

    private PermissionEntity permission(String name) {
        PermissionEntity p = new PermissionEntity();
        p.setName(name);
        return p;
    }

    private AppRoleEntity role(String name, String... permissionNames) {
        AppRoleEntity r = new AppRoleEntity();
        r.setName(name);
        for (String pName : permissionNames) {
            r.getPermissions().add(permission(pName));
        }
        return r;
    }

    private User user(boolean withCompany, Set<AppRoleEntity> roles) {
        Company c = withCompany ? company(1) : null;
        User u = new User("jdoe", "secret", roles, null, c);
        u.setId(42L);
        u.setActive(true);
        return u;
    }

    // ── toDTO ─────────────────────────────────────────────────────────────────

    @Nested @DisplayName("toDTO")
    class ToDTO {

        @Test @DisplayName("maps id, userName and active from the User entity")
        void mapsBasicFields() {
            User u = user(true, Set.of());
            UserContextDTO dto = sut.toDTO(u);

            assertThat(dto.id()).isEqualTo(42L);
            assertThat(dto.userName()).isEqualTo("jdoe");
            assertThat(dto.active()).isTrue();
        }

        @Test @DisplayName("maps company to CompanyContextDTO when present")
        void mapsCompany_whenPresent() {
            User u = user(true, Set.of());
            CompanyContextDTO companyDTO = sut.toDTO(u).company();

            assertThat(companyDTO).isNotNull();
            assertThat(companyDTO.id()).isEqualTo(1);
            assertThat(companyDTO.name()).isEqualTo("Empresa Test");
            assertThat(companyDTO.nit()).isEqualTo("900-1");
        }

        @Test @DisplayName("company is null in DTO when User has no company")
        void mapsCompany_whenNull() {
            User u = user(false, Set.of());
            assertThat(sut.toDTO(u).company()).isNull();
        }

        @Test @DisplayName("roles list contains role names sorted alphabetically")
        void rolesSorted() {
            User u = user(true, Set.of(role("USER"), role("ADMIN")));
            assertThat(sut.toDTO(u).roles()).containsExactly("ADMIN", "USER");
        }

        @Test @DisplayName("permissions are flattened from roles and sorted")
        void permissionsFlattenedAndSorted() {
            AppRoleEntity admin = role("ADMIN", "ROUTE_READ", "VEHICLE_WRITE");
            AppRoleEntity viewer = role("USER",  "ROUTE_READ", "PERSON_READ");
            User u = user(true, Set.of(admin, viewer));

            // ROUTE_READ appears in both roles but must appear only once (Set)
            assertThat(sut.toDTO(u).permissions())
                    .containsExactly("PERSON_READ", "ROUTE_READ", "VEHICLE_WRITE")
                    .doesNotHaveDuplicates();
        }

        @Test @DisplayName("user with no roles produces empty roles and permissions lists")
        void noRoles_emptyLists() {
            User u = user(true, Set.of());
            UserContextDTO dto = sut.toDTO(u);

            assertThat(dto.roles()).isEmpty();
            assertThat(dto.permissions()).isEmpty();
        }

        @Test @DisplayName("inactive user maps active=false")
        void inactiveUser() {
            User u = user(true, Set.of());
            u.setActive(false);
            assertThat(sut.toDTO(u).active()).isFalse();
        }
    }
}
