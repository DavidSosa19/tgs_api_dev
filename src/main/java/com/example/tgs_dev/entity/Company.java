package com.example.tgs_dev.entity;

import com.example.tgs_dev.entity.enums.SchedulingMode;
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

    /**
     * Discriminates which schedule-initialisation strategy this company uses.
     * Defaults to {@link SchedulingMode#ROTATION_BASED} to preserve existing
     * behaviour for all companies created before V7.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scheduling_mode", nullable = false, length = 30)
    private SchedulingMode schedulingMode = SchedulingMode.ROTATION_BASED;

    public Company(String name, String nit) {
        this.name = name;
        this.nit  = nit;
    }
}
