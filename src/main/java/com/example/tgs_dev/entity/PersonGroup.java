package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stable business identity for a {@link Person} across SCD Type-2 versions.
 *
 * <p>Every {@code Person} row now carries a {@code group_id} FK pointing here.
 * When data changes (e.g. documentNumber correction), the current row is closed
 * ({@code is_current = false, version_to = today}) and a new row is created with
 * the same {@code group_id}.  Historical FK references in
 * {@link DriverAssignment}, {@link OperationEvent}, etc. continue pointing to the
 * specific version that was active at that time.
 *
 * <p>{@code natural_key} stores {@code documentNumber} at group-creation time
 * (or {@code "PERSON-{id}"} when {@code documentNumber} was {@code null}).
 * It is used to detect duplicates and to display the "canonical" identifier of
 * the person concept, independent of which version is current.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "person_group", schema = "core")
public class PersonGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Document number at group-creation time (or synthetic fallback). */
    @Column(name = "natural_key", nullable = false)
    private String naturalKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PersonGroup(Company company, String naturalKey) {
        this.company    = company;
        this.naturalKey = naturalKey;
    }
}
