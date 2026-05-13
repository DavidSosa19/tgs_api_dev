package com.example.tgs_dev.repository.specification;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public class CommonSpecifications {
    public static <T> Specification<T> fieldEquals(String field, Object value) {
        return (root, query, cb) ->
                cb.equal(root.get(field), value);
    }

    public static <T> Specification<T> fieldLike(String field, String value) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%");
    }

    public static <T, Y extends Comparable<Y>> Specification<T> fieldGreaterThan(
            String field, Y value) {
        return (root, query, cb) ->
                cb.greaterThan(root.get(field), value);
    }

    public static <T, Y extends Comparable<Y>> Specification<T> fieldLessThan(
            String field, Y value) {
        return (root, query, cb) ->
                cb.lessThan(root.get(field), value);
    }


    public static <T, Y extends Comparable<Y>> Specification<T> fieldBetween(
            String field, Y from, Y to) {
        return (root, query, cb) ->
                cb.between(root.get(field), from, to);
    }

    public static <T> Specification<T> fieldIsNull(String field) {
        return (root, query, cb) ->
                cb.isNull(root.get(field));
    }

    public static <T> Specification<T> fieldIn(String field, Collection<?> values) {
        return (root, query, cb) ->
                root.get(field).in(values);
    }
    public static <T, Y extends Comparable<Y>> Specification<T> fieldGreaterThanOrEqualTo(
            String field, Y value) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get(field), value);
    }

    public static <T, Y extends Comparable<Y>> Specification<T> fieldLessThanOrEqualTo(
            String field, Y value) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get(field), value);
    }
}
