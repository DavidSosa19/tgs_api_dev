package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.repository.base.BaseRepository;

import java.util.Optional;

public interface CompanyRepository extends BaseRepository<Company, Integer> {
    Optional<Company> findByNit(String nit);
}
