package com.example.tgs_dev.controller.response;

public record VehicleDTO(
        Integer id,
        String vehicleNumber,
        String licensePlate,
        Boolean active,
        PersonDTO owner
) {}
