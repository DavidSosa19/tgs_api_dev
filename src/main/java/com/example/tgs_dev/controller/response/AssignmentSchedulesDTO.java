package com.example.tgs_dev.controller.response;

import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;

import java.util.List;

public record AssignmentSchedulesDTO(
        VehicleAssignment vehicleAssignment,
        List<Schedule> schedules
) {
}
