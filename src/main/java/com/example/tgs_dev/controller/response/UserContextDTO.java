package com.example.tgs_dev.controller.response;

import java.util.List;

/**
 * Contexto completo del usuario autenticado devuelto por {@code GET /api/auth/me}.
 *
 * <p>El frontend usa esta respuesta para:
 * <ul>
 *   <li>Mostrar el nombre de usuario y la empresa activa en la topbar.</li>
 *   <li>Construir el conjunto de permisos efectivos para la lógica RBAC UI.</li>
 *   <li>Filtrar menús, botones y rutas según los permisos del usuario.</li>
 * </ul>
 *
 * @param id          ID del usuario.
 * @param userName    Nombre de usuario (login).
 * @param roles       Nombres de los roles asignados (p. ej. "ADMIN", "USER").
 * @param permissions Permisos efectivos aplanados desde los roles (p. ej. "ROUTE_READ").
 *                    Estos son los mismos valores que usa {@code @PreAuthorize}.
 * @param company     Empresa a la que pertenece el usuario.
 * @param active      Si la cuenta está activa.
 */
public record UserContextDTO(
        Long              id,
        String            userName,
        List<String>      roles,
        List<String>      permissions,
        CompanyContextDTO company,
        Boolean           active
) {}
