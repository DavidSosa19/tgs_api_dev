package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Stable business identity for a {@link Route} across SCD Type-2 versions.
 *
 * <p>{@code natural_key} holds {@code routeNumber} at group-creation time.
 * Uniqueness is now enforced <em>per-company</em> (fixing the pre-existing global
 * constraint on {@code core.route.route_number}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "route_group", schema = "core")
public class RouteGroup {

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

    public RouteGroup(Company company, String naturalKey) {
        this.company    = company;
        this.naturalKey = naturalKey;
    }
}
