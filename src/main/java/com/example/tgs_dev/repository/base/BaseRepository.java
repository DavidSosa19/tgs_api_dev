package com.example.tgs_dev.repository.base;

import com.example.tgs_dev.repository.filter.FilterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.List;

@NoRepositoryBean
public interface BaseRepository<T, I extends Serializable>
        extends JpaRepository<T, I>, JpaSpecificationExecutor<T> {

    /**
     * Filtra por criterios dinámicos sin restricción de tenant.
     *
     * @deprecated Use {@link #filter(FilterRequest, Pageable, Specification)} with a
     * {@link com.example.tgs_dev.repository.specification.TenantSpecifications#belongsToCompany}
     * specification instead. This overload returns data from all companies and will be
     * removed once all callers have been migrated.
     */
    @Deprecated(since = "V5", forRemoval = true)
    Page<T> filter(FilterRequest request, Pageable pageable);

    /**
     * Filtra por criterios dinámicos ANDeando una {@link Specification} extra
     * (p. ej. la restricción de tenant {@code company_id = ?}).
     */
    Page<T> filter(FilterRequest request, Pageable pageable, Specification<T> extra);

    /** Marca un único registro como inactivo (active = false). */
    void softDelete(T entity);

    /** Marca una lista de registros como inactivos en una sola query. */
    void softDeleteAll(List<T> entities);
}
