package com.example.tgs_dev.repository.base;

import com.example.tgs_dev.entity.Activatable;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.filter.GenericSpecification;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.util.List;

public class BaseRepositoryImpl<T, I extends Serializable>
        extends SimpleJpaRepository<T, I> implements BaseRepository<T, I> {

    // EntityManager stored directly so softDelete / softDeleteAll can call
    // em.merge() without routing through the Spring proxy (avoiding S6809).
    private final EntityManager entityManager;

    public BaseRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public Page<T> filter(FilterRequest request, Pageable pageable) {
        return findAll(new GenericSpecification<>(request), pageable);
    }

    /**
     * Marks a single entity as inactive ({@code active = false}) and merges it
     * into the current persistence context via {@link EntityManager#merge(Object)}.
     *
     * <p>Entities must implement {@link Activatable} (i.e. expose a
     * {@code setActive(Boolean)} setter) for this method to have any effect.</p>
     */
    @Override
    @Transactional
    public void softDelete(T entity) {
        if (entity instanceof Activatable a) {
            a.setActive(false);
            entityManager.merge(entity);
        }
    }

    /**
     * Marks every entity in the list as inactive and merges each one into the
     * current persistence context.
     */
    @Override
    @Transactional
    public void softDeleteAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) return;
        entities.forEach(e -> {
            if (e instanceof Activatable a) {
                a.setActive(false);
                entityManager.merge(e);
            }
        });
    }
}
