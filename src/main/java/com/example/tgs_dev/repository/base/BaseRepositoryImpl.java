package com.example.tgs_dev.repository.base;

import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.filter.GenericSpecification;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.util.List;

public class BaseRepositoryImpl<T, I extends Serializable>
        extends SimpleJpaRepository<T, I> implements BaseRepository<T, I> {

    private final JpaEntityInformation<T, I> entityInformation;
    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public BaseRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = (JpaEntityInformation<T, I>) entityInformation;
        this.entityManager = entityManager;
    }

    @Override
    public Page<T> filter(FilterRequest request) {
        GenericSpecification<T> spec = new GenericSpecification<>(request);

        Sort sort = Sort.by(
                Sort.Direction.fromString(request.getSortDirection()),
                request.getSortBy() != null ? request.getSortBy() : "id"
        );

        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
        return findAll(spec, pageable);
    }

    @Override
    @Transactional
    public void softDelete(T entity) {
        I id = entityInformation.getId(entity);
        String entityName = entityInformation.getEntityName();
        entityManager.createQuery(
                "UPDATE " + entityName + " e SET e.active = false WHERE e.id = :id"
        ).setParameter("id", id).executeUpdate();
    }

    @Override
    @Transactional
    public void softDeleteAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) return;
        List<I> ids = entities.stream()
                .map(entityInformation::getId)
                .toList();
        String entityName = entityInformation.getEntityName();
        entityManager.createQuery(
                "UPDATE " + entityName + " e SET e.active = false WHERE e.id IN :ids"
        ).setParameter("ids", ids).executeUpdate();
    }
}
