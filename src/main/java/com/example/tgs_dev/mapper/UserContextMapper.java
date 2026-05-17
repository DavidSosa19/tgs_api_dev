package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.CompanyContextDTO;
import com.example.tgs_dev.controller.response.UserContextDTO;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte un {@link User} en el DTO de contexto enviado al frontend
 * como respuesta de {@code GET /api/auth/me}.
 */
@Component
public class UserContextMapper {

    /**
     * Mapea el usuario autenticado a su contexto frontend.
     *
     * <p>Los <em>permisos</em> se obtienen de {@link User#getAuthorities()},
     * que ya aplana el grafo roles → permisos.  Esto garantiza que el frontend
     * recibe exactamente los mismos permisos que evalúa {@code @PreAuthorize}.
     */
    public UserContextDTO toDTO(User user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .sorted()
                .toList();

        List<String> permissions = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();

        CompanyContextDTO companyDTO = user.getCompany() != null
                ? toCompanyDTO(user.getCompany())
                : null;

        return new UserContextDTO(
                user.getId(),
                user.getUsername(),
                roles,
                permissions,
                companyDTO,
                user.isEnabled()
        );
    }

    private CompanyContextDTO toCompanyDTO(Company company) {
        return new CompanyContextDTO(company.getId(), company.getName(), company.getNit());
    }
}
