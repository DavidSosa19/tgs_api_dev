package com.example.tgs_dev.controller.response;

import java.util.List;

public record UserDTO(
        Long id,
        String userName,
        List<String> roles,
        Boolean active,
        PersonDTO person
) {}
