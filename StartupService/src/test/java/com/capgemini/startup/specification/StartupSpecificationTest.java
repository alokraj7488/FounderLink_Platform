package com.capgemini.startup.specification;

import com.capgemini.startup.entity.Startup;
import com.capgemini.startup.enums.StartupStage;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import static org.mockito.Mockito.*;

class StartupSpecificationTest {

    @Test
    void isApproved_shouldReturnPredicate() {
        Specification<Startup> spec = StartupSpecification.isApproved();
        Root<Startup> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);

        when(root.get("isApproved")).thenReturn(path);
        spec.toPredicate(root, query, cb);

        verify(cb).isTrue(path);
    }

    @Test
    void hasIndustry_shouldReturnPredicate() {
        Specification<Startup> spec = StartupSpecification.hasIndustry("Tech");
        Root<Startup> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);

        when(root.get("industry")).thenReturn(path);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq("tech"));
    }

    @Test
    void hasStage_shouldReturnPredicate() {
        Specification<Startup> spec = StartupSpecification.hasStage(StartupStage.MVP);
        Root<Startup> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);

        when(root.get("stage")).thenReturn(path);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(path, StartupStage.MVP);
    }

    @Test
    void hasMinFunding_shouldReturnPredicate() {
        BigDecimal min = new BigDecimal("1000");
        Specification<Startup> spec = StartupSpecification.hasMinFunding(min);
        Root<Startup> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);

        when(root.get("fundingGoal")).thenReturn(path);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(path, min);
    }

    @Test
    void hasMaxFunding_shouldReturnPredicate() {
        BigDecimal max = new BigDecimal("5000");
        Specification<Startup> spec = StartupSpecification.hasMaxFunding(max);
        Root<Startup> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);

        when(root.get("fundingGoal")).thenReturn(path);
        spec.toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(path, max);
    }

    @Test
    void hasLocation_shouldReturnPredicate() {
        Specification<Startup> spec = StartupSpecification.hasLocation("Berlin");
        Root<Startup> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);

        when(root.get("location")).thenReturn(path);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq("berlin"));
    }
}
