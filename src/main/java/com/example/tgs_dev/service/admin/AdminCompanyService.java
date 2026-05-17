package com.example.tgs_dev.service.admin;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.admin.CreateCompanyRequest;
import com.example.tgs_dev.controller.request.admin.UpdateCompanyRequest;
import com.example.tgs_dev.controller.response.admin.CompanyAdminDTO;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.security.Permissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cross-tenant company management service for the SUPER_ADMIN admin module.
 *
 * <p>Security layers:
 * <ol>
 *   <li>{@code SecurityConfig} — path-level {@code hasAuthority(SUPER_ADMIN_ACCESS)}</li>
 *   <li>{@code @PreAuthorize} on {@link com.example.tgs_dev.controller.admin.AdminCompanyController}</li>
 *   <li>{@link #assertSuperAdmin()} — defense-in-depth inside each public method</li>
 * </ol>
 *
 * <p>This service deliberately does <strong>not</strong> use
 * {@link com.example.tgs_dev.repository.specification.TenantSpecifications} —
 * operations here are intentionally cross-tenant.
 */
@Service
public class AdminCompanyService {

    private final CompanyRepository companyRepository;

    public AdminCompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<CompanyAdminDTO> findAll() {
        assertSuperAdmin();
        return companyRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public CompanyAdminDTO findById(Integer id) {
        assertSuperAdmin();
        return toDTO(loadOrThrow(id));
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public CompanyAdminDTO create(CreateCompanyRequest request) {
        assertSuperAdmin();
        Company company = new Company(request.name(), request.nit());
        return toDTO(companyRepository.save(company));
    }

    @Transactional
    public CompanyAdminDTO update(Integer id, UpdateCompanyRequest request) {
        assertSuperAdmin();
        Company company = loadOrThrow(id);
        company.setName(request.name());
        company.setNit(request.nit());
        return toDTO(companyRepository.save(company));
    }

    @Transactional
    public void deactivate(Integer id) {
        assertSuperAdmin();
        Company company = loadOrThrow(id);
        companyRepository.softDelete(company);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Company loadOrThrow(Integer id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("notFound.company|" + id));
    }

    private CompanyAdminDTO toDTO(Company c) {
        return new CompanyAdminDTO(c.getId(), c.getName(), c.getNit(), c.getActive());
    }

    /**
     * Defense-in-depth: verifies the calling thread has the SUPER_ADMIN_ACCESS authority.
     * This guard fires even if the request bypassed the controller {@code @PreAuthorize}.
     */
    private void assertSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals(Permissions.SUPER_ADMIN_ACCESS))) {
            throw new AccessDeniedException("admin.access.denied");
        }
    }
}
