package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.VehicleRotation;
import com.example.tgs_dev.repository.RotationEntryRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RotationEntryService {

    private final RotationEntryRepository rotationEntryRepository;

    public RotationEntryService(RotationEntryRepository rotationEntryRepository) {
        this.rotationEntryRepository = rotationEntryRepository;
    }

    public RotationEntry save(RotationEntry rotationEntry){
        return rotationEntryRepository.save(rotationEntry);
    }

    public List<RotationEntry> saveAll(VehicleRotation rotation,List<RotationEntry> entries){
        for (RotationEntry entry: entries){
            entry.setVehicleRotation(rotation);
        }
        return rotationEntryRepository.saveAll(entries);
    }

    public Optional<RotationEntry> findById(Integer id){
        return rotationEntryRepository.findById(id);
    }

    public List<RotationEntry> findAll(){ return rotationEntryRepository.findAll(); }

    public void delete(RotationEntry rotationEntry){
        rotationEntryRepository.delete(rotationEntry);
    }

    public void deleteAll(List<RotationEntry> entries){
        rotationEntryRepository.deleteAll(entries);
    }

    public List<RotationEntry> findByRotation(VehicleRotation vehicleRotation){
        return  rotationEntryRepository.findAll(
                CommonSpecifications.fieldEquals("vehicleRotation",vehicleRotation),
                Sort.by(Sort.Direction.ASC,"listPosition")
        );
    }
}
