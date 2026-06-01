package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stable business identity for a {@link ScheduleTemplate} across SCD Type-2 versions.
 *
 * <p>{@code natural_key} holds {@code templateNumber} at group-creation time.
 * {@link com.example.tgs_dev.entity.RotationEntry} and
 * {@link com.example.tgs_dev.entity.VehicleAssignment} continue to reference
 * the specific {@link ScheduleTemplate} version that was active when the record
 * was created — giving correct historical data without joins to this table.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "schedule_template_group", schema = "core")
public class ScheduleTemplateGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "natural_key", nullable = false)
    private String naturalKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ScheduleTemplateGroup(Company company, String naturalKey) {
        this.company    = company;
        this.naturalKey = naturalKey;
    }
}
