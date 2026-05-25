package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends BaseRepository<Company, Integer> {

    Optional<Company> findByNit(String nit);

    // ── Admin cross-tenant queries (bypass @SQLRestriction) ──────────────────

    /**
     * Returns ALL companies (active and inactive) for admin listing.
     * Bypasses the {@code @SQLRestriction("active = true")} on {@link Company}.
     */
    @Query(nativeQuery = true,
            value = "SELECT * FROM core.company ORDER BY created_at DESC")
    List<Company> findAllAdmin();

    /**
     * Finds a company by ID regardless of active status.
     * Bypasses the {@code @SQLRestriction("active = true")} on {@link Company}.
     */
    @Query(nativeQuery = true,
            value = "SELECT * FROM core.company WHERE id = :id")
    Optional<Company> findByIdAdmin(@Param("id") Integer id);

    /**
     * Reactivates a previously deactivated company (sets active = true).
     * Bypasses the {@code @SQLRestriction("active = true")} on {@link Company}.
     */
    @Modifying
    @Query(nativeQuery = true,
            value = "UPDATE core.company SET active = true WHERE id = :id")
    void reactivateById(@Param("id") Integer id);
}
