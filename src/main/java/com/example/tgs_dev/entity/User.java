package com.example.tgs_dev.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users", schema = "core")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@NullMarked
public class User implements UserDetails, Activatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Nullable
    private Long id;

    @Column(name = "user_name", nullable = false, unique = true)
    private String userName;

    @Column(nullable = false)
    private String password;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            schema = "core",
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<AppRoleEntity> roles = new HashSet<>();

    /**
     * 1-to-1 relationship: one person can be linked to at most one user account.
     * The DB enforces this via UNIQUE constraint on users.person_id (V6 migration).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    @Nullable
    private Person person;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /** Constructor de uso en la capa de servicio al registrar un nuevo usuario. */
    public User(String userName, String password, @Nullable Set<AppRoleEntity> roles, @Nullable Person person, Company company) {
        this.userName = userName;
        this.password = password;
        this.roles    = roles != null ? roles : new HashSet<>();
        this.person   = person;
        this.company  = company;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null) return Set.of();
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(p -> new SimpleGrantedAuthority(p.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getUsername() { return userName; }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return Boolean.TRUE.equals(active); }
}
