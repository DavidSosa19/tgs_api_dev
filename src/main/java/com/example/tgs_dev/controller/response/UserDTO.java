package com.example.tgs_dev.controller.response;

public record UserDTO(
        Long id,
        String userName,
        String rol,
        Boolean active,
        PersonDTO person
) {}
