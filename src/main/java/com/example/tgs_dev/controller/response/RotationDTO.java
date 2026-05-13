package com.example.tgs_dev.controller.response;

import java.util.List;

public record RotationDTO(
        VehicleRotationDTO rotation,
        List<RotationEntryDTO> entryList
) {}
