package com.example.tgs_dev.repository.filter;

import jakarta.persistence.criteria.*;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class GenericSpecification<T> implements Specification<T> {

    private final FilterRequest filterRequest;

    public GenericSpecification(FilterRequest filterRequest) {
        this.filterRequest = filterRequest;
    }

    @Override
    public Predicate toPredicate(@NonNull Root<T> root,
                                 @NonNull CriteriaQuery<?> query,
                                 @NonNull CriteriaBuilder cb) {
        List<Predicate> predicates = filterRequest.getFilters().stream()
                .map(c -> buildPredicate(c, root, cb))
                .filter(Objects::nonNull)
                .toList();

        if (predicates.isEmpty()) return cb.conjunction();

        Predicate[] arr = predicates.toArray(new Predicate[0]);
        return filterRequest.getLogic() == LogicOperator.OR ? cb.or(arr) : cb.and(arr);
    }

    @SuppressWarnings("unchecked")
    private Predicate buildPredicate(FilterCriteria c, Root<T> root, CriteriaBuilder cb) {
        Path<?> path = resolvePath(root, c.getField());
        Object coerced = coerceValue(c.getValue(), path.getJavaType());

        return switch (c.getOperator()) {
            case EQUALS                -> cb.equal(path, coerced);
            case NOT_EQUALS            -> cb.notEqual(path, coerced);
            case LIKE                  -> cb.like(
                    cb.lower((Path<String>) path),
                    "%" + c.getValue().toString().toLowerCase() + "%");
            case NOT_LIKE              -> cb.notLike(
                    cb.lower((Path<String>) path),
                    "%" + c.getValue().toString().toLowerCase() + "%");
            case GREATER_THAN          -> comparablePredicate(path, coerced, cb, "gt");
            case GREATER_THAN_OR_EQUAL -> comparablePredicate(path, coerced, cb, "gte");
            case LESS_THAN             -> comparablePredicate(path, coerced, cb, "lt");
            case LESS_THAN_OR_EQUAL    -> comparablePredicate(path, coerced, cb, "lte");
            case BETWEEN               -> betweenPredicate(path,
                    coerceValue(c.getValue(), path.getJavaType()),
                    coerceValue(c.getValueTo(), path.getJavaType()), cb);
            case IN                    -> path.in((Collection<?>) c.getValue());
            case IS_NULL               -> cb.isNull(path);
            case IS_NOT_NULL           -> cb.isNotNull(path);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object coerceValue(Object value, Class<?> targetType) {
        if (value == null || targetType == null) return value;
        if (targetType.isInstance(value))        return value;

        String str = value.toString();

        if (targetType == LocalDate.class)      return LocalDate.parse(str);
        if (targetType == LocalDateTime.class)  return LocalDateTime.parse(str);
        if (targetType == Integer.class || targetType == int.class) return Integer.valueOf(str);
        if (targetType == Long.class    || targetType == long.class) return Long.valueOf(str);
        if (targetType == Double.class  || targetType == double.class) return Double.valueOf(str);
        if (targetType == Boolean.class || targetType == boolean.class) return Boolean.valueOf(str);
        if (targetType.isEnum())                return Enum.valueOf((Class<Enum>) targetType, str);

        return value;
    }

    @SuppressWarnings("unchecked")
    private <Y extends Comparable<Y>> Predicate comparablePredicate(
            Path<?> path, Object value, CriteriaBuilder cb, String op) {
        Path<Y> p = (Path<Y>) path;
        Y v = (Y) value;
        return switch (op) {
            case "gt"  -> cb.greaterThan(p, v);
            case "gte" -> cb.greaterThanOrEqualTo(p, v);
            case "lt"  -> cb.lessThan(p, v);
            case "lte" -> cb.lessThanOrEqualTo(p, v);
            default    -> throw new IllegalArgumentException("Operador desconocido: " + op);
        };
    }

    @SuppressWarnings("unchecked")
    private <Y extends Comparable<Y>> Predicate betweenPredicate(
            Path<?> path, Object from, Object to, CriteriaBuilder cb) {
        return cb.between((Path<Y>) path, (Y) from, (Y) to);
    }

    private Path<?> resolvePath(Root<T> root, String field) {
        String[] parts = field.split("\\.");
        Path<?> path = root.get(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            path = ((Path<?>) path).get(parts[i]);
        }
        return path;
    }
}
