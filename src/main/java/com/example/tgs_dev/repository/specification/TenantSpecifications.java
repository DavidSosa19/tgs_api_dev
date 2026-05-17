package com.example.tgs_dev.repository.specification;

import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable JPA {@link Specification} fragments for multi-tenant data isolation.
 *
 * <p>Every entity that carries a {@code company} association uses
 * {@link #belongsToCompany(Integer)} to restrict query results to the current
 * tenant's records only.
 */
public final class TenantSpecifications {

    private TenantSpecifications() {}

    /**
     * Matches entities whose {@code company.id} equals the given {@code companyId}.
     *
     * <p>Assumes the entity has a {@code company} field mapped to a {@link com.example.tgs_dev.entity.Company}.
     *
     * @param companyId the tenant's company primary key; must not be {@code null}.
     */
    public static <T> Specification<T> belongsToCompany(Integer companyId) {
        return (root, query, cb) ->
                cb.equal(root.get("company").get("id"), companyId);
    }
}
