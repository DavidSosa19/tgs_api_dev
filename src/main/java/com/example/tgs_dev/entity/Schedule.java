package com.example.tgs_dev.entity;

import com.example.tgs_dev.entity.enums.ScheduleOrigin;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A single departure slot for a vehicle within an operation.
 *
 * <h3>Lifecycle</h3>
 * Schedules follow a soft-delete + lineage model so historical and current
 * plans can both be reconstructed:
 * <ul>
 *   <li>{@link #active} — {@code true} = operationally in effect; {@code false}
 *       = preserved for audit but no longer the plan of record.</li>
 *   <li>{@link #origin} — how the row was created (see {@link ScheduleOrigin}).</li>
 *   <li>{@link #originalDepartureTime} — for derived rows
 *       ({@code RECALCULATED}, {@code REPLACEMENT}), the departure time of the
 *       corresponding {@code ORIGINAL} row.  {@code null} for {@code ORIGINAL}
 *       rows (their own {@link #departureTime} is the original).</li>
 *   <li>{@link #supersededAt} / {@link #supersededReason} — populated only when
 *       {@code active = false}.  A database CHECK constraint enforces this
 *       lifecycle invariant.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name="schedule", schema = "core")
public class Schedule extends BaseAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "schedule_id_seq")
    @SequenceGenerator(name = "schedule_id_seq", sequenceName = "core.schedule_id_seq", allocationSize = 50)
    @Column(name="id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_assignment_id", nullable = false)
    private VehicleAssignment vehicleAssignment;

    /**
     * Global position of this departure within the day's slot sequence for the
     * route (1-based).  Two consecutive {@code departureOrder} values belong to
     * different vehicles spaced by the resolved headway.
     */
    @Column(name="departure_order", nullable = false)
    private Integer departureOrder;

    /**
     * Per-vehicle trip number (1-based): "this is the N-th departure of THIS
     * vehicle within the day".  Derived from the round-robin slot assignment as
     * {@code slotIndex / vehicleCount + 1}.
     *
     * <p>Independent from {@link #departureOrder}: a vehicle's tripNumber=2 may
     * have departureOrder=12 (slot 12 of the day).
     */
    @Column(name="trip_number", nullable = false)
    private Integer tripNumber;

    @Column(name="departure_time", nullable = false)
    private LocalTime departureTime;

    // ── Lifecycle fields ──────────────────────────────────────────────────────

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private ScheduleOrigin origin = ScheduleOrigin.ORIGINAL;

    /**
     * Departure time of the corresponding {@code ORIGINAL} row.  Used by the
     * front-end to display "was X, now Y" diffs without extra queries.
     *
     * <p>{@code null} when {@link #origin} is {@link ScheduleOrigin#ORIGINAL} —
     * for those rows, {@link #departureTime} itself is the original time.
     */
    @Column(name = "original_departure_time")
    private LocalTime originalDepartureTime;

    /** Timestamp when {@link #active} flipped to {@code false}.  Null while active. */
    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    /** Why this row was deactivated (see {@code ScheduleSupersededReason}).  Null while active. */
    @Column(name = "superseded_reason", length = 40)
    private String supersededReason;

    public Schedule(VehicleAssignment vehicleAssignment,
                    Integer departureOrder,
                    Integer tripNumber,
                    LocalTime departureTime) {
        this.vehicleAssignment = vehicleAssignment;
        this.departureOrder    = departureOrder;
        this.tripNumber        = tripNumber;
        this.departureTime     = departureTime;
    }

    /**
     * Marks this schedule as superseded with the given reason and timestamp.
     * Idempotent — calling on an already-inactive row preserves the original
     * supersession metadata.
     */
    public void supersede(String reason, LocalDateTime at) {
        if (Boolean.FALSE.equals(this.active)) return;
        this.active           = false;
        this.supersededAt     = at;
        this.supersededReason = reason;
    }
}
