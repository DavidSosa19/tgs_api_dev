package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Rol de aplicación parametrizable (p. ej. ADMIN, USER).
 * Los roles agrupan permisos y se asignan a usuarios.
 * Agregar un rol: INSERT en core.app_role + asignar permisos en core.role_permission.
 */
@Entity
@Table(name = "app_role", schema = "core")
@Getter
@Setter
@NoArgsConstructor
@SQLRestriction("active = true")
public class AppRoleEntity extends BaseAudit implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Nombre del rol — coincide con las constantes de {@link com.example.tgs_dev.security.AppRole}. */
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    private Boolean active = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            schema = "core",
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();
}
