package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.admin.CreateAdminUserRequest;
import com.example.tgs_dev.controller.request.admin.UpdateAdminUserRequest;
import com.example.tgs_dev.controller.response.admin.UserAdminDTO;
import com.example.tgs_dev.entity.AppRoleEntity;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.repository.AppRoleRepository;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.repository.PersonGroupRepository;
import com.example.tgs_dev.repository.PersonRepository;
import com.example.tgs_dev.repository.UserRepository;
import com.example.tgs_dev.security.AppRole;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.admin.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService")
class AdminUserServiceTest {

    @Mock UserRepository        userRepository;
    @Mock PersonRepository      personRepository;
    @Mock PersonGroupRepository personGroupRepository;
    @Mock CompanyRepository     companyRepository;
    @Mock AppRoleRepository     appRoleRepository;
    @Mock PasswordEncoder       passwordEncoder;

    AdminUserService service;

    static final Company COMPANY = company(1);
    static final AppRoleEntity USER_ROLE  = role(1, AppRole.USER);
    static final AppRoleEntity ADMIN_ROLE = role(2, AppRole.ADMIN);

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository, personRepository, personGroupRepository,
                companyRepository, appRoleRepository, passwordEncoder);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    private static Company company(int id) {
        Company c = new Company("Company " + id, "NIT-" + id);
        c.setId(id);
        return c;
    }

    private static AppRoleEntity role(int id, String name) {
        AppRoleEntity r = new AppRoleEntity();
        r.setId(id);
        r.setName(name);
        r.setPermissions(new HashSet<>());
        return r;
    }

    private Person person(int id) {
        Person p = new Person("DOC-" + id, "First", null, "Last", null);
        p.setId(id);
        p.setCompany(COMPANY);
        return p;
    }

    private User user(int id) {
        Person p = person(id);
        User u = new User("user" + id, "hashed", Set.of(USER_ROLE), p, COMPANY);
        u.setId((long) id);
        // @Builder.Default removes the field initializer (= true) from non-builder
        // constructors, so active must be set explicitly here.
        u.setActive(true);
        return u;
    }

    private User inactiveUser(int id) {
        User u = user(id);
        u.setActive(false);
        return u;
    }

    // ── Security helpers ──────────────────────────────────────────────────────

    private void authenticateAsSuperAdmin() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null,
                Set.of(new SimpleGrantedAuthority(Permissions.SUPER_ADMIN_ACCESS))));
        SecurityContextHolder.setContext(ctx);
    }

    private void authenticateAsRegularUser() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null,
                Set.of(new SimpleGrantedAuthority(Permissions.ROUTE_READ))));
        SecurityContextHolder.setContext(ctx);
    }

    // ── assertSuperAdmin ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("assertSuperAdmin — defense-in-depth")
    class AssertSuperAdmin {

        @Test
        @DisplayName("throws AccessDeniedException when no authentication")
        void noAuth() {
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> service.findAll())
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("throws AccessDeniedException for regular user")
        void regularUser() {
            authenticateAsRegularUser();
            assertThatThrownBy(() -> service.findAll())
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all users including inactive via admin query")
        void returnsAllIncludingInactive() {
            authenticateAsSuperAdmin();
            when(userRepository.findAllAdmin()).thenReturn(List.of(user(1), inactiveUser(2)));

            List<UserAdminDTO> result = service.findAll();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserAdminDTO::active).containsExactlyInAnyOrder(true, false);
        }
    }

    // ── findByCompany ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCompany")
    class FindByCompany {

        @Test
        @DisplayName("returns all users for a company including inactive")
        void filtered() {
            authenticateAsSuperAdmin();
            when(userRepository.findAllByCompanyIdAdmin(1)).thenReturn(List.of(user(1), inactiveUser(2)));

            List<UserAdminDTO> result = service.findByCompany(1);

            assertThat(result).hasSize(2);
            assertThat(result.getFirst().companyId()).isEqualTo(1);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns DTO when user exists")
        void found() {
            authenticateAsSuperAdmin();
            when(userRepository.findByIdAdmin(1L)).thenReturn(Optional.of(user(1)));

            UserAdminDTO dto = service.findById(1L);

            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.companyName()).isEqualTo("Company 1");
        }

        @Test
        @DisplayName("returns inactive user DTO")
        void foundInactive() {
            authenticateAsSuperAdmin();
            when(userRepository.findByIdAdmin(2L)).thenReturn(Optional.of(inactiveUser(2)));

            UserAdminDTO dto = service.findById(2L);

            assertThat(dto.active()).isFalse();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            authenticateAsSuperAdmin();
            when(userRepository.findByIdAdmin(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates user with existing person")
        void withExistingPerson() {
            authenticateAsSuperAdmin();
            Person p = person(5);
            when(companyRepository.findById(1)).thenReturn(Optional.of(COMPANY));
            when(appRoleRepository.findById(1)).thenReturn(Optional.of(USER_ROLE));
            when(personRepository.findById(5)).thenReturn(Optional.of(p));
            when(userRepository.existsByPerson_Id(5)).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            CreateAdminUserRequest request = new CreateAdminUserRequest(
                    1, "newuser", "password123", 1, 5,
                    null, null, null, null, null);

            UserAdminDTO dto = service.create(request);

            assertThat(dto.userName()).isEqualTo("newuser");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("creates user with inline person")
        void withInlinePerson() {
            authenticateAsSuperAdmin();
            when(companyRepository.findById(1)).thenReturn(Optional.of(COMPANY));
            when(appRoleRepository.findById(1)).thenReturn(Optional.of(USER_ROLE));
            when(personGroupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(personRepository.save(any(Person.class))).thenAnswer(i -> {
                Person p = i.getArgument(0);
                p.setId(99);
                return p;
            });
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

            CreateAdminUserRequest request = new CreateAdminUserRequest(
                    1, "newuser2", "password123", 1, null,
                    "DOC-99", "John", null, "Doe", null);

            service.create(request);

            verify(personRepository).save(any(Person.class));
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("throws BusinessException when SUPER_ADMIN role is assigned")
        void superAdminRoleForbidden() {
            authenticateAsSuperAdmin();
            AppRoleEntity superAdminRole = role(99, AppRole.SUPER_ADMIN);
            when(companyRepository.findById(1)).thenReturn(Optional.of(COMPANY));
            when(appRoleRepository.findById(99)).thenReturn(Optional.of(superAdminRole));

            CreateAdminUserRequest request = new CreateAdminUserRequest(
                    1, "newuser", "pass12345", 99, null,
                    "DOC-1", "A", null, "B", null);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("superAdminForbidden");
        }

        @Test
        @DisplayName("throws BusinessException when person already has a user")
        void personAlreadyHasUser() {
            authenticateAsSuperAdmin();
            Person p = person(5);
            when(companyRepository.findById(1)).thenReturn(Optional.of(COMPANY));
            when(appRoleRepository.findById(1)).thenReturn(Optional.of(USER_ROLE));
            when(personRepository.findById(5)).thenReturn(Optional.of(p));
            when(userRepository.existsByPerson_Id(5)).thenReturn(true);

            CreateAdminUserRequest request = new CreateAdminUserRequest(
                    1, "newuser", "pass12345", 1, 5,
                    null, null, null, null, null);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("alreadyHasUser");
        }

        @Test
        @DisplayName("throws BusinessException when person belongs to different company")
        void personWrongCompany() {
            authenticateAsSuperAdmin();
            Company otherCompany = company(2);
            Person p = person(5);
            p.setCompany(otherCompany);

            when(companyRepository.findById(1)).thenReturn(Optional.of(COMPANY));
            when(appRoleRepository.findById(1)).thenReturn(Optional.of(USER_ROLE));
            when(personRepository.findById(5)).thenReturn(Optional.of(p));

            CreateAdminUserRequest request = new CreateAdminUserRequest(
                    1, "newuser", "pass12345", 1, 5,
                    null, null, null, null, null);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("wrongCompany");
        }

        @Test
        @DisplayName("throws BusinessException when inline person missing required fields")
        void missingPersonFields() {
            authenticateAsSuperAdmin();
            when(companyRepository.findById(1)).thenReturn(Optional.of(COMPANY));
            when(appRoleRepository.findById(1)).thenReturn(Optional.of(USER_ROLE));

            CreateAdminUserRequest request = new CreateAdminUserRequest(
                    1, "newuser", "pass12345", 1, null,
                    null, null, null, null, null);

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("requiredFieldsMissing");
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates role and active status")
        void updates() {
            authenticateAsSuperAdmin();
            User u = user(1);
            when(userRepository.findByIdAdmin(1L)).thenReturn(Optional.of(u));
            when(appRoleRepository.findById(2)).thenReturn(Optional.of(ADMIN_ROLE));
            when(userRepository.save(u)).thenReturn(u);

            UserAdminDTO dto = service.update(1L, new UpdateAdminUserRequest(2, false, null));

            assertThat(dto.active()).isFalse();
            assertThat(dto.roles()).contains(AppRole.ADMIN);
        }

        @Test
        @DisplayName("updates password when newPassword is provided")
        void updatesPassword() {
            authenticateAsSuperAdmin();
            User u = user(1);
            when(userRepository.findByIdAdmin(1L)).thenReturn(Optional.of(u));
            when(appRoleRepository.findById(1)).thenReturn(Optional.of(USER_ROLE));
            when(passwordEncoder.encode("newpass123")).thenReturn("new_hashed");
            when(userRepository.save(u)).thenReturn(u);

            service.update(1L, new UpdateAdminUserRequest(1, true, "newpass123"));

            verify(passwordEncoder).encode("newpass123");
            assertThat(u.getPassword()).isEqualTo("new_hashed");
        }

        @Test
        @DisplayName("does not change password when newPassword is null")
        void doesNotChangePasswordWhenNull() {
            authenticateAsSuperAdmin();
            User u = user(1);
            String originalPassword = u.getPassword();
            when(userRepository.findByIdAdmin(1L)).thenReturn(Optional.of(u));
            when(appRoleRepository.findById(1)).thenReturn(Optional.of(USER_ROLE));
            when(userRepository.save(u)).thenReturn(u);

            service.update(1L, new UpdateAdminUserRequest(1, true, null));

            assertThat(u.getPassword()).isEqualTo(originalPassword);
        }

        @Test
        @DisplayName("throws BusinessException when assigning SUPER_ADMIN role")
        void superAdminRoleForbidden() {
            authenticateAsSuperAdmin();
            User u = user(1);
            AppRoleEntity superAdminRole = role(99, AppRole.SUPER_ADMIN);
            when(userRepository.findByIdAdmin(1L)).thenReturn(Optional.of(u));
            when(appRoleRepository.findById(99)).thenReturn(Optional.of(superAdminRole));
            UpdateAdminUserRequest req = new UpdateAdminUserRequest(99, true, null);

            assertThatThrownBy(() -> service.update(1L, req))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ── deactivate ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("calls softDelete on the user")
        void deactivates() {
            authenticateAsSuperAdmin();
            User u = user(1);
            when(userRepository.findByIdAdmin(1L)).thenReturn(Optional.of(u));

            service.deactivate(1L);

            verify(userRepository).softDelete(u);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void notFound() {
            authenticateAsSuperAdmin();
            when(userRepository.findByIdAdmin(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivate(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── reactivate ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("calls reactivateById when user exists")
        void reactivates() {
            authenticateAsSuperAdmin();
            when(userRepository.findByIdAdmin(2L)).thenReturn(Optional.of(inactiveUser(2)));

            service.reactivate(2L);

            verify(userRepository).reactivateById(2L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user not found")
        void notFound() {
            authenticateAsSuperAdmin();
            when(userRepository.findByIdAdmin(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reactivate(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("throws AccessDeniedException when not super admin")
        void accessDenied() {
            authenticateAsRegularUser();
            assertThatThrownBy(() -> service.reactivate(1L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
