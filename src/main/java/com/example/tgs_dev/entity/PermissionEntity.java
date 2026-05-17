package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;

/**
 * Permiso atómico del sistema (p. ej. ROUTE_READ, VEHICLE_WRITE).
 * Los permisos se asignan a roles, nunca directamente a usuarios.
 */
@Entity
@Table(name = "permission", schema = "core")
@Getter
@Setter
@NoArgsConstructor
@SQLRestriction("active = true")
public class PermissionEntity implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Nombre técnico — coincide con las constantes de {@link com.example.tgs_dev.security.Permissions}. */
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false)
    private Boolean active = true;
}
