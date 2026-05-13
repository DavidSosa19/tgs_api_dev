package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.VehicleDTO;
import com.example.tgs_dev.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VehicleMapper {

    private final PersonMapper personMapper;

    public VehicleDTO toDTO(Vehicle vehicle) {
        if (vehicle == null) return null;
        return new VehicleDTO(
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getLicensePlate(),
                vehicle.getActive(),
                personMapper.toDTO(vehicle.getOwner())
        );
    }

    public List<VehicleDTO> toDTOList(List<Vehicle> vehicles) {
        return vehicles.stream().map(this::toDTO).toList();
    }
}
