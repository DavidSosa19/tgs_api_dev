package com.example.tgs_dev.controller.response;

/**
 * Read model for a {@link com.example.tgs_dev.entity.Person}.
 *
 * <p>{@code groupId} is the stable business identity ({@code person_group.id}) used
 * for navigation ({@code GET /persons/{groupId}}).
 * {@code id} is the surrogate version ID — kept for FK resolution in historical
 * records (e.g. {@code DriverAssignment}, {@code OperationEvent}).
 */
public record PersonDTO(
        Integer id,
        Long    groupId,
        String  documentNumber,
        String  firstName,
        String  secondName,
        String  firstLastName,
        String  secondLastName,
        Boolean active
) {}
