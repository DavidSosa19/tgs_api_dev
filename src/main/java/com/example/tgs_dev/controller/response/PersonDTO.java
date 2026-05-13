package com.example.tgs_dev.controller.response;

public record PersonDTO(
        Integer id,
        String documentNumber,
        String firstName,
        String secondName,
        String firstLastName,
        String secondLastName,
        Boolean active
) {}
