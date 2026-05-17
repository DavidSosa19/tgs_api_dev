package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.repository.CompanyRepository;
import com.example.tgs_dev.security.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Single point of access to the current tenant's {@link Company}.
 *
 * <p>Services that need to scope repository queries to the authenticated user's
 * company inject this service instead of each managing their own
 * {@link TenantContext} lookups.
 */
@Service
public class TenantService {

    private final CompanyRepository companyRepository;

    public TenantService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Returns the company ID stored in the current thread's {@link TenantContext}.
     *
     * @throws IllegalStateException if no tenant context has been established.
     */
    public Integer currentCompanyId() {
        return TenantContext.require();
    }

    /**
     * Loads and returns the {@link Company} for the current tenant.
     *
     * @throws IllegalStateException if no tenant context has been established or the
     *                               company no longer exists.
     */
    public Company currentCompany() {
        Integer id = currentCompanyId();
        return companyRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant company not found for id=" + id +
                        ". Verify V3 migration has been applied."));
    }
}
