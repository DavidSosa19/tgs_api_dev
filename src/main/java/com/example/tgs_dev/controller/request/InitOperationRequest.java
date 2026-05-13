package com.example.tgs_dev.controller.request;

import com.example.tgs_dev.entity.Route;

import java.time.LocalDate;

public record InitOperationRequest(
        Route route,
        LocalDate date
) {
}
