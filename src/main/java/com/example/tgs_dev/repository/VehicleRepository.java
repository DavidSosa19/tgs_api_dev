package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.repository.base.BaseRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends BaseRepository<Vehicle,Integer> {

}
