package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.AppRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRoleEntity, Integer> {

    Optional<AppRoleEntity> findByName(String name);
}
