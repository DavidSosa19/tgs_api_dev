package com.example.tgs_dev.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("TenantSpecifications")
class TenantSpecificationsTest {

    @Test
    @DisplayName("belongsToCompany produces an equality predicate on company.id")
    void belongsToCompany_producesEqualityPredicate() {
        // Arrange
        Integer companyId = 7;

        Root<Object>        root    = mock(Root.class);
        CriteriaQuery<?>    query   = mock(CriteriaQuery.class);
        CriteriaBuilder     cb      = mock(CriteriaBuilder.class);
        Path<Object>        companyPath = mock(Path.class);
        Path<Object>        idPath      = mock(Path.class);
        Predicate           predicate   = mock(Predicate.class);

        when(root.get("company")).thenReturn(companyPath);
        when(companyPath.get("id")).thenReturn(idPath);
        when(cb.equal(idPath, companyId)).thenReturn(predicate);

        // Act
        Specification<Object> spec = TenantSpecifications.belongsToCompany(companyId);
        Predicate result = spec.toPredicate(root, query, cb);

        // Assert
        assertThat(result).isSameAs(predicate);
        verify(root).get("company");
        verify(companyPath).get("id");
        verify(cb).equal(idPath, companyId);
    }

    @Test
    @DisplayName("belongsToCompany works for different company ids (no state leakage)")
    void belongsToCompany_differentIds_noStateLeakage() {
        Root<Object>     root1 = mock(Root.class);
        Root<Object>     root2 = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder  cb    = mock(CriteriaBuilder.class);

        Path<Object> path1 = mock(Path.class);
        Path<Object> path2 = mock(Path.class);
        Path<Object> id1   = mock(Path.class);
        Path<Object> id2   = mock(Path.class);

        when(root1.get("company")).thenReturn(path1);
        when(root2.get("company")).thenReturn(path2);
        when(path1.get("id")).thenReturn(id1);
        when(path2.get("id")).thenReturn(id2);

        Predicate pred1 = mock(Predicate.class);
        Predicate pred2 = mock(Predicate.class);
        when(cb.equal(id1, 1)).thenReturn(pred1);
        when(cb.equal(id2, 2)).thenReturn(pred2);

        Specification<Object> spec1 = TenantSpecifications.belongsToCompany(1);
        Specification<Object> spec2 = TenantSpecifications.belongsToCompany(2);

        assertThat(spec1.toPredicate(root1, query, cb)).isSameAs(pred1);
        assertThat(spec2.toPredicate(root2, query, cb)).isSameAs(pred2);
    }
}
