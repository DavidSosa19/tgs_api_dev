package com.example.tgs_dev.entity;

import com.example.tgs_dev.service.strategy.AssignmentSlot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="rotation_entry", schema = "core")
/**
 * Persistence entity that also acts as an {@link AssignmentSlot}: Lombok's
 * {@code @Getter} already generates {@code getVehicle()} and
 * {@code getScheduleTemplate()}, satisfying the interface contract with zero
 * extra code.
 */
public class RotationEntry extends BaseAudit implements AssignmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ScheduleTemplate scheduleTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rotation_id", nullable = false)
    private VehicleRotation vehicleRotation;

    @Column(name="list_position")
    private int listPosition;
}
