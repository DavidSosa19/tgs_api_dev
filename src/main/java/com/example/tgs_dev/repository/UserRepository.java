package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.User;
import com.example.tgs_dev.repository.base.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends BaseRepository<User, Integer> {

    Optional<User> findByUserName(String userName);

    // ── Admin cross-tenant queries ────────────────────────────────────────────

    /** Returns all active users belonging to a given company (no TenantSpec needed). */
    List<User> findAllByCompany_Id(Integer companyId);

    /** Checks whether a Person is already linked to a user account (1:1 enforcement). */
    boolean existsByPerson_Id(Integer personId);
}
