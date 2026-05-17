package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.admin.CreateCompanyRequest;
import com.example.tgs_dev.controller.request.admin.UpdateCompanyRequest;
import com.example.tgs_dev.controller.response.admin.CompanyAdminDTO;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.security.Permissions;
import com.example.tgs_dev.service.admin.AdminCompanyService;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCompanyService")
class AdminCompanyServiceTest {

    @Mock CompanyRepository companyRepository;

    AdminCompanyService service;

    @BeforeEach
    void setUp() {
        service = new AdminCompanyService(companyRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Security helpers ──────────────────────────────────────────────────────

    private void authenticateAsSuperAdmin() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                "superadmin", null,
                Set.of(new SimpleGrantedAuthority(Permissions.SUPER_ADMIN_ACCESS),
                       new SimpleGrantedAuthority(Permissions.COMPANY_READ),
                       new SimpleGrantedAuthority(Permissions.COMPANY_WRITE),
                       new SimpleGrantedAuthority(Permissions.COMPANY_DEACTIVATE))));
        SecurityContextHolder.setContext(ctx);
    }

    private void authenticateAsRegularUser() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null,
                Set.of(new SimpleGrantedAuthority(Permissions.ROUTE_READ))));
        SecurityContextHolder.setContext(ctx);
    }

    private Company company(int id) {
        Company c = new Company("Company " + id, "NIT-" + id);
        c.setId(id);
        return c;
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

        @Test
        @DisplayName("passes for SUPER_ADMIN user")
        void superAdmin() {
            authenticateAsSuperAdmin();
            when(companyRepository.findAll()).thenReturn(List.of());
            assertThat(service.findAll()).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all companies as DTOs")
        void returnsList() {
            authenticateAsSuperAdmin();
            when(companyRepository.findAll()).thenReturn(List.of(company(1), company(2)));

            List<CompanyAdminDTO> result = service.findAll();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(CompanyAdminDTO::id).containsExactlyInAnyOrder(1, 2);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns DTO when company exists")
        void found() {
            authenticateAsSuperAdmin();
            when(companyRepository.findById(1)).thenReturn(Optional.of(company(1)));

            CompanyAdminDTO dto = service.findById(1);

            assertThat(dto.id()).isEqualTo(1);
            assertThat(dto.name()).isEqualTo("Company 1");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when not found")
        void notFound() {
            authenticateAsSuperAdmin();
            when(companyRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("saves and returns new company DTO")
        void creates() {
            authenticateAsSuperAdmin();
            Company saved = company(10);
            when(companyRepository.save(any(Company.class))).thenReturn(saved);

            CompanyAdminDTO dto = service.create(new CreateCompanyRequest("Acme", "900-1"));

            assertThat(dto.id()).isEqualTo(10);
            verify(companyRepository).save(any(Company.class));
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("updates name and nit")
        void updates() {
            authenticateAsSuperAdmin();
            Company existing = company(1);
            when(companyRepository.findById(1)).thenReturn(Optional.of(existing));
            when(companyRepository.save(existing)).thenReturn(existing);

            CompanyAdminDTO dto = service.update(1, new UpdateCompanyRequest("New Name", "NIT-999"));

            assertThat(dto.name()).isEqualTo("New Name");
            assertThat(dto.nit()).isEqualTo("NIT-999");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when company not found")
        void notFound() {
            authenticateAsSuperAdmin();
            when(companyRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99, new UpdateCompanyRequest("X", "Y")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── deactivate ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("calls softDelete on the company")
        void deactivates() {
            authenticateAsSuperAdmin();
            Company c = company(1);
            when(companyRepository.findById(1)).thenReturn(Optional.of(c));

            service.deactivate(1);

            verify(companyRepository).softDelete(c);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when company not found")
        void notFound() {
            authenticateAsSuperAdmin();
            when(companyRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deactivate(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
