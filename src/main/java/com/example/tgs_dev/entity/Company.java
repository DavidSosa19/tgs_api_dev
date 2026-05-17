package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company", schema = "core")
@SQLRestriction("active = true")
public class Company extends BaseAudit implements Activatable, Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "nit", nullable = false, unique = true)
    private String nit;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public Company(String name, String nit) {
        this.name = name;
        this.nit  = nit;
    }
}
