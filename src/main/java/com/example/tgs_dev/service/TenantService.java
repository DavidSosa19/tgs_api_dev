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
     * Loads and returns the fully-hydrated {@link Company} for the current tenant.
     * Use this only when you need to read fields on the company (e.g.
     * {@code getSchedulingMode()}).  For setting a Company as an FK on another
     * entity, prefer {@link #currentCompanyReference()} — it avoids the SELECT.
     *
     * @throws IllegalStateException if no tenant context has been established or
     *                               the company no longer exists.
     */
    public Company currentCompany() {
        Integer id = currentCompanyId();
        return companyRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant company not found for id=" + id +
                        ". Verify V3 migration has been applied."));
    }

    /**
     * Returns a Hibernate proxy reference to the current tenant's {@link Company}
     * <strong>without</strong> issuing a SELECT.  The proxy carries only the id,
     * which is sufficient for setting it as an FK on a child entity.
     *
     * <p>Accessing any field on the returned proxy triggers a lazy load.
     * Use {@link #currentCompany()} when you need real field values.
     *
     * @throws IllegalStateException if no tenant context has been established.
     */
    public Company currentCompanyReference() {
        return companyRepository.getReferenceById(currentCompanyId());
    }
}
