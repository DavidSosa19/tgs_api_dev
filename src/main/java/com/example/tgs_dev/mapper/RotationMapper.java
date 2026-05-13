package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.request.RotationRequest;
import com.example.tgs_dev.controller.response.RotationDTO;
import com.example.tgs_dev.controller.response.RotationEntryDTO;
import com.example.tgs_dev.controller.response.VehicleRotationDTO;
import com.example.tgs_dev.entity.RotationEntry;
import com.example.tgs_dev.entity.VehicleRotation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RotationMapper {

    private final VehicleMapper vehicleMapper;
    private final ScheduleTemplateMapper scheduleTemplateMapper;

    public VehicleRotationDTO toRotationDTO(VehicleRotation rotation) {
        if (rotation == null) return null;
        return new VehicleRotationDTO(
                rotation.getId(),
                rotation.getStartDate(),
                rotation.getEndDate(),
                rotation.getActive(),
                rotation.getRotationType()
        );
    }

    public RotationEntryDTO toEntryDTO(RotationEntry entry) {
        if (entry == null) return null;
        return new RotationEntryDTO(
                entry.getId(),
                entry.getListPosition(),
                vehicleMapper.toDTO(entry.getVehicle()),
                scheduleTemplateMapper.toDTO(entry.getScheduleTemplate())
        );
    }

    public RotationDTO toDTO(VehicleRotation rotation, List<RotationEntry> entries) {
        return new RotationDTO(
                toRotationDTO(rotation),
                entries.stream().map(this::toEntryDTO).toList()
        );
    }

    public List<VehicleRotationDTO> toRotationDTOList(List<VehicleRotation> rotations) {
        return rotations.stream().map(this::toRotationDTO).toList();
    }

    public VehicleRotation toEntity(RotationRequest request) {
        return new VehicleRotation(
                request.startDate(),
                request.endDate(),
                request.active() != null ? request.active() : true,
                request.rotationType()
        );
    }

    public void updateEntity(VehicleRotation rotation, RotationRequest request) {
        rotation.setStartDate(request.startDate());
        rotation.setEndDate(request.endDate());
        rotation.setRotationType(request.rotationType());
        if (request.active() != null) {
            rotation.setActive(request.active());
        }
    }
}
