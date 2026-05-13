package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Person;
import com.example.tgs_dev.entity.Vehicle;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.repository.VehicleRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Vehicle save(Vehicle vehicle){
        return vehicleRepository.save(vehicle);
    }

    public Vehicle findById(Integer id){
        return vehicleRepository.findById(id)
                .orElseThrow(()->new NoSuchElementException("notFound.vehicle|" + id));
    }

    public List<Vehicle> findAll(){ return vehicleRepository.findAll(); }

    public void delete(Vehicle vehicle){
        vehicleRepository.softDelete(vehicle);
    }

    public Optional<Vehicle> findByNumber(String vehicleNumber){
        return vehicleRepository.findOne(CommonSpecifications.fieldEquals("vehicleNumber", vehicleNumber));
    }

    public Page<Vehicle> filter(FilterRequest request) {
        return vehicleRepository.filter(request);
    }
}
