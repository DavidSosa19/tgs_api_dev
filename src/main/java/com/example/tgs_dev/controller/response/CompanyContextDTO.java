package com.example.tgs_dev.controller.response;

/**
 * Contexto de empresa enviado al frontend en el endpoint {@code GET /api/auth/me}.
 * Solo expone los campos necesarios para la UI (nombre y NIT para identificación visual).
 */
public record CompanyContextDTO(Integer id, String name, String nit) {}
