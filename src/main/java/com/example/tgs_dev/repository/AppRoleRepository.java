package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.AppRoleEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRoleEntity, Integer> {

    Optional<AppRoleEntity> findByName(String name);

    /** Returns all assignable roles — excludes SUPER_ADMIN (not grantable via UI). */
    @Query("SELECT r FROM AppRoleEntity r WHERE r.name <> 'SUPER_ADMIN' ORDER BY r.name")
    List<AppRoleEntity> findAssignable();
}
