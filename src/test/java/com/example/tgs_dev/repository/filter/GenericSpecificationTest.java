package com.example.tgs_dev.repository.filter;

import com.example.tgs_dev.entity.enums.ShiftDayType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
@DisplayName("GenericSpecification")
class GenericSpecificationTest {

    @Mock Root root;
    @Mock CriteriaQuery query;
    @Mock CriteriaBuilder cb;
    @Mock Path path;
    @Mock Path parentPath;
    @Mock Predicate predicate;
    @Mock Predicate conjunctionPredicate;
    @Mock Predicate disjunctionPredicate;
    @Mock Expression lowerExpr;

    @BeforeEach
    void setUp() {
        lenient().when(root.get(anyString())).thenReturn(path);
        lenient().when(path.getJavaType()).thenReturn((Class) String.class);
        lenient().when(cb.conjunction()).thenReturn(conjunctionPredicate);
        lenient().when(cb.disjunction()).thenReturn(disjunctionPredicate);
        lenient().when(cb.equal(any(), any())).thenReturn(predicate);
        lenient().when(cb.notEqual(any(), any())).thenReturn(predicate);
        lenient().when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        lenient().when(cb.notLike(any(Expression.class), anyString())).thenReturn(predicate);
        lenient().when(cb.isNull(any(Expression.class))).thenReturn(predicate);
        lenient().when(cb.isNotNull(any(Expression.class))).thenReturn(predicate);
        lenient().when(cb.greaterThan(any(Path.class), (Comparable) any())).thenReturn(predicate);
        lenient().when(cb.greaterThanOrEqualTo(any(Path.class), (Comparable) any())).thenReturn(predicate);
        lenient().when(cb.lessThan(any(Path.class), (Comparable) any())).thenReturn(predicate);
        lenient().when(cb.lessThanOrEqualTo(any(Path.class), (Comparable) any())).thenReturn(predicate);
        lenient().when(cb.between(any(Path.class), (Comparable) any(), (Comparable) any())).thenReturn(predicate);
        lenient().when(cb.lower(any(Expression.class))).thenReturn(lowerExpr);
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(predicate);
        lenient().when(cb.or(any(Predicate[].class))).thenReturn(predicate);
    }

    private FilterRequest requestWith(List<FilterCriteria> criteria, LogicOperator logic) {
        return new FilterRequest(criteria, logic, "id", "asc", 0, 10);
    }

    private FilterCriteria criteria(String field, FilterOperator op, Object value, Object valueTo) {
        return new FilterCriteria(field, op, value, valueTo);
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("toPredicate structure")
    class ToPredicateStructure {

        @Test
        @DisplayName("empty filters → cb.conjunction() returned directly")
        void emptyFilters_returnsConjunction() {
            var spec = new GenericSpecification<>(requestWith(List.of(), LogicOperator.AND));
            var result = spec.toPredicate(root, query, cb);
            assertThat(result).isSameAs(conjunctionPredicate);
        }

        @Test
        @DisplayName("AND logic → cb.and called (not cb.or or cb.conjunction)")
        void andLogic_callsCbAnd() {
            // Use anyString() so Mockito dispatches to equal(Expression<?>, Object) — not the Expression<?>,Expression<?> overload
            lenient().when(cb.equal(any(Expression.class), anyString())).thenReturn(predicate);
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("name", FilterOperator.EQUALS, "Alice", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).and((Predicate[]) any());
            verify(cb, never()).or((Predicate[]) any());
        }

        @Test
        @DisplayName("OR logic → cb.or called (not cb.and or cb.conjunction)")
        void orLogic_callsCbOr() {
            // Use anyString() so Mockito dispatches to equal(Expression<?>, Object) — not the Expression<?>,Expression<?> overload
            lenient().when(cb.equal(any(Expression.class), anyString())).thenReturn(predicate);
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("status", FilterOperator.EQUALS, "ACTIVE", null)), LogicOperator.OR));
            spec.toPredicate(root, query, cb);
            verify(cb).or((Predicate[]) any());
            verify(cb, never()).and((Predicate[]) any());
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("string operators")
    class StringOperators {

        @Test
        @DisplayName("EQUALS (String) → cb.equal")
        void equals_callsCbEqual() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("name", FilterOperator.EQUALS, "Alice", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).equal(path, "Alice");
        }

        @Test
        @DisplayName("NOT_EQUALS → cb.notEqual")
        void notEquals_callsCbNotEqual() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("name", FilterOperator.NOT_EQUALS, "Alice", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).notEqual(path, "Alice");
        }

        @Test
        @DisplayName("LIKE → cb.lower then cb.like with %value%")
        void like_callsLowerAndLike() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("name", FilterOperator.LIKE, "alice", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).lower(path);
            verify(cb).like(lowerExpr, "%alice%");
        }

        @Test
        @DisplayName("NOT_LIKE → cb.lower then cb.notLike with %value%")
        void notLike_callsLowerAndNotLike() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("name", FilterOperator.NOT_LIKE, "bob", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).lower(path);
            verify(cb).notLike(lowerExpr, "%bob%");
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("comparable operators")
    class ComparableOperators {

        @Test
        @DisplayName("GREATER_THAN → cb.greaterThan")
        void greaterThan() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("count", FilterOperator.GREATER_THAN, "5", null)), LogicOperator.AND));
            lenient().when(path.getJavaType()).thenReturn((Class) Integer.class);
            spec.toPredicate(root, query, cb);
            verify(cb).greaterThan(any(Path.class), (Comparable) any());
        }

        @Test
        @DisplayName("GREATER_THAN_OR_EQUAL → cb.greaterThanOrEqualTo")
        void greaterThanOrEqual() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("count", FilterOperator.GREATER_THAN_OR_EQUAL, "5", null)), LogicOperator.AND));
            lenient().when(path.getJavaType()).thenReturn((Class) Integer.class);
            spec.toPredicate(root, query, cb);
            verify(cb).greaterThanOrEqualTo(any(Path.class), (Comparable) any());
        }

        @Test
        @DisplayName("LESS_THAN → cb.lessThan")
        void lessThan() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("count", FilterOperator.LESS_THAN, "10", null)), LogicOperator.AND));
            lenient().when(path.getJavaType()).thenReturn((Class) Integer.class);
            spec.toPredicate(root, query, cb);
            verify(cb).lessThan(any(Path.class), (Comparable) any());
        }

        @Test
        @DisplayName("LESS_THAN_OR_EQUAL → cb.lessThanOrEqualTo")
        void lessThanOrEqual() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("count", FilterOperator.LESS_THAN_OR_EQUAL, "10", null)), LogicOperator.AND));
            lenient().when(path.getJavaType()).thenReturn((Class) Integer.class);
            spec.toPredicate(root, query, cb);
            verify(cb).lessThanOrEqualTo(any(Path.class), (Comparable) any());
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("special operators")
    class SpecialOperators {

        @Test
        @DisplayName("IS_NULL → cb.isNull")
        void isNull() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("deletedAt", FilterOperator.IS_NULL, null, null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).isNull(path);
        }

        @Test
        @DisplayName("IS_NOT_NULL → cb.isNotNull")
        void isNotNull() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("deletedAt", FilterOperator.IS_NOT_NULL, null, null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).isNotNull(path);
        }

        @Test
        @DisplayName("BETWEEN → cb.between with from and to values")
        void between() {
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("age", FilterOperator.BETWEEN, "18", "65")), LogicOperator.AND));
            lenient().when(path.getJavaType()).thenReturn((Class) Integer.class);
            spec.toPredicate(root, query, cb);
            verify(cb).between(any(Path.class), (Comparable) any(), (Comparable) any());
        }

        @Test
        @DisplayName("IN → path.in(Collection)")
        void in() {
            lenient().when(path.in(any(java.util.Collection.class))).thenReturn(predicate);
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("status", FilterOperator.IN, List.of("A", "B", "C"), null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(path).in(any(java.util.Collection.class));
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("type coercion")
    class TypeCoercion {

        @Test
        @DisplayName("coerceValue: String value '42' with Integer path type → Integer 42")
        void coerceInteger() {
            lenient().when(path.getJavaType()).thenReturn((Class) Integer.class);
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("count", FilterOperator.EQUALS, "42", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).equal(path, 42);
        }

        @Test
        @DisplayName("coerceValue: String '2024-01-15' with LocalDate path type → LocalDate")
        void coerceLocalDate() {
            lenient().when(path.getJavaType()).thenReturn((Class) LocalDate.class);
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("date", FilterOperator.EQUALS, "2024-01-15", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).equal(path, LocalDate.of(2024, 1, 15));
        }

        @Test
        @DisplayName("coerceValue: String 'true' with Boolean path type → Boolean.TRUE")
        void coerceBoolean() {
            lenient().when(path.getJavaType()).thenReturn((Class) Boolean.class);
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("active", FilterOperator.EQUALS, "true", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).equal(path, Boolean.TRUE);
        }

        @Test
        @DisplayName("coerceValue: Enum string 'BUSINESS_DAYS' with ShiftDayType path type → ShiftDayType.BUSINESS_DAYS")
        void coerceEnum() {
            lenient().when(path.getJavaType()).thenReturn((Class) ShiftDayType.class);
            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("dayType", FilterOperator.EQUALS, "BUSINESS_DAYS", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);
            verify(cb).equal(path, ShiftDayType.BUSINESS_DAYS);
        }
    }

    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("path resolution")
    class PathResolution {

        @Test
        @DisplayName("nested dot-path 'vehicle.id' → root.get('vehicle').get('id')")
        void nestedDotPath() {
            lenient().when(root.get("vehicle")).thenReturn(parentPath);
            lenient().when(parentPath.get("id")).thenReturn(path);
            lenient().when(path.getJavaType()).thenReturn((Class) Long.class);
            lenient().when(cb.equal(any(), any())).thenReturn(predicate);

            var spec = new GenericSpecification<>(
                    requestWith(List.of(criteria("vehicle.id", FilterOperator.EQUALS, "99", null)), LogicOperator.AND));
            spec.toPredicate(root, query, cb);

            verify(root).get("vehicle");
            verify(parentPath).get("id");
        }
    }
}
