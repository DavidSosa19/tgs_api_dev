package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteGroup;
import com.example.tgs_dev.repository.RouteGroupRepository;
import com.example.tgs_dev.repository.RouteOperationRepository;
import com.example.tgs_dev.repository.RouteRepository;
import com.example.tgs_dev.repository.ScheduleTemplateRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RouteService} — SCD Type-2 behaviour.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RouteService")
class RouteServiceTest {

    @Mock RouteRepository            routeRepository;
    @Mock RouteGroupRepository       routeGroupRepository;
    @Mock ScheduleTemplateRepository scheduleTemplateRepository;
    @Mock RouteOperationRepository   routeOperationRepository;
    @Mock TenantService              tenantService;
    @InjectMocks RouteService sut;

    private static final int  COMPANY_ID = 1;
    private static final Long GROUP_ID   = 50L;

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(company());
    }

    private static Company company() {
        Company c = new Company("ACME", "NIT-1");
        c.setId(COMPANY_ID);
        return c;
    }

    private static RouteGroup group() {
        RouteGroup g = new RouteGroup(company(), "R-1");
        g.setId(GROUP_ID);
        return g;
    }

    private static Route route() {
        Route r = new Route("R-1");
        r.setId(1);
        r.setCompany(company());
        r.setGroup(group());
        r.setIsCurrent(true);
        return r;
    }

    private static RouteRequest request() {
        return new RouteRequest("R-1");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create")
    class Create {
        @Test @DisplayName("creates a group + first version stamped with the tenant company")
        void createsGroupAndFirstVersion() {
            when(routeGroupRepository.save(any(RouteGroup.class))).thenAnswer(i -> i.getArgument(0));
            when(routeRepository.save(any(Route.class))).thenAnswer(i -> i.getArgument(0));

            Route result = sut.create(request());

            assertThat(result.getCompany().getId()).isEqualTo(COMPANY_ID);
            assertThat(result.getGroup()).isNotNull();
            assertThat(result.getIsCurrent()).isTrue();
            assertThat(result.getVersionTo()).isNull();
            verify(routeGroupRepository).save(any(RouteGroup.class));
            verify(routeRepository).save(any(Route.class));
        }
    }

    // ── findByGroupId ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByGroupId")
    class FindByGroupId {
        @Test @DisplayName("returns current version (active or deactivated) so the UI can offer reactivation")
        void found() {
            Route r = route();
            when(routeRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(r));
            assertThat(sut.findByGroupId(GROUP_ID)).isSameAs(r);
        }

        @Test @DisplayName("throws ResourceNotFoundException when no current version exists")
        void notFound() {
            when(routeRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.empty());
            assertThatThrownBy(() -> sut.findByGroupId(GROUP_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {
        @Test @DisplayName("closes current version and opens a new one preserving the group")
        void closesAndOpensVersion() {
            Route current = route();
            when(routeRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(routeRepository.save(any(Route.class))).thenAnswer(i -> i.getArgument(0));

            Route next = sut.update(GROUP_ID, new RouteRequest("R-2"));

            assertThat(current.getIsCurrent()).isFalse();
            assertThat(next.getIsCurrent()).isTrue();
            assertThat(next.getRouteNumber()).isEqualTo("R-2");
            assertThat(next.getGroup()).isSameAs(current.getGroup());
            verify(routeRepository, times(2)).save(any(Route.class));
        }
    }

    // ── deactivate ───────────────────────────────────────────────────────────────

    @Nested @DisplayName("deactivate")
    class Deactivate {
        @Test @DisplayName("soft-deletes current version when no FK references exist")
        void delegates() {
            Route current = route();
            when(routeRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(scheduleTemplateRepository.existsByRouteIdAndActiveTrue(current.getId())).thenReturn(false);
            when(scheduleTemplateRepository.existsBySecondaryRouteIdAndActiveTrue(current.getId())).thenReturn(false);
            when(routeOperationRepository.existsByRouteIdAndActiveTrue(current.getId())).thenReturn(false);

            sut.deactivate(GROUP_ID);

            verify(routeRepository).softDelete(current);
        }

        @Test @DisplayName("throws when route is primary in an active template")
        void blockedByPrimaryTemplate() {
            Route current = route();
            when(routeRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(scheduleTemplateRepository.existsByRouteIdAndActiveTrue(current.getId())).thenReturn(true);

            assertThatThrownBy(() -> sut.deactivate(GROUP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fk.routeInTemplate");
            verify(routeRepository, never()).softDelete(any());
        }

        @Test @DisplayName("throws when route is secondary in an active template")
        void blockedBySecondaryTemplate() {
            Route current = route();
            when(routeRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(scheduleTemplateRepository.existsByRouteIdAndActiveTrue(current.getId())).thenReturn(false);
            when(scheduleTemplateRepository.existsBySecondaryRouteIdAndActiveTrue(current.getId())).thenReturn(true);

            assertThatThrownBy(() -> sut.deactivate(GROUP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fk.routeSecondaryInTemplate");
            verify(routeRepository, never()).softDelete(any());
        }

        @Test @DisplayName("throws when route is used in an active operation")
        void blockedByActiveOperation() {
            Route current = route();
            when(routeRepository.findCurrentByGroupId(GROUP_ID, COMPANY_ID))
                    .thenReturn(Optional.of(current));
            when(scheduleTemplateRepository.existsByRouteIdAndActiveTrue(current.getId())).thenReturn(false);
            when(scheduleTemplateRepository.existsBySecondaryRouteIdAndActiveTrue(current.getId())).thenReturn(false);
            when(routeOperationRepository.existsByRouteIdAndActiveTrue(current.getId())).thenReturn(true);

            assertThatThrownBy(() -> sut.deactivate(GROUP_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fk.routeInOperation");
            verify(routeRepository, never()).softDelete(any());
        }
    }

    // ── findAll / findAllCurrent ────────────────────────────────────────────────

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("returns active routes sorted, scoped to tenant")
        void returnsActiveSorted() {
            List<Route> list = List.of(route());
            when(routeRepository.findAllActiveByCompanySorted(COMPANY_ID)).thenReturn(list);
            assertThat(sut.findAll()).isSameAs(list);
            verify(routeRepository).findAllActiveByCompanySorted(COMPANY_ID);
        }

        @Test @DisplayName("findAllCurrent returns all current versions including inactive")
        void returnsAllCurrent() {
            List<Route> list = List.of(route());
            when(routeRepository.findAllCurrentByCompany(COMPANY_ID)).thenReturn(list);
            assertThat(sut.findAllCurrent()).isSameAs(list);
            verify(routeRepository).findAllCurrentByCompany(COMPANY_ID);
        }
    }

    // ── findByNumber ──────────────────────────────────────────────────────────

    @Nested @DisplayName("findByNumber")
    class FindByNumber {
        @Test @DisplayName("returns Optional with route when found")
        void found() {
            Route r = route();
            when(routeRepository.findOne(any(Specification.class))).thenReturn(Optional.of(r));
            assertThat(sut.findByNumber("R-1")).contains(r);
        }

        @Test @DisplayName("returns empty Optional when not found")
        void empty() {
            when(routeRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
            assertThat(sut.findByNumber("NONE")).isEmpty();
        }
    }

    // ── filter ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("filter")
    class Filter {
        @Test @DisplayName("delegates to repo with tenant + active Specification")
        void delegates() {
            FilterRequest req  = new FilterRequest(List.of(), null, "id", "ASC", 0, 10);
            Page<Route>   page = new PageImpl<>(List.of(route()));
            when(routeRepository.filter(eq(req), any(), any(Specification.class))).thenReturn(page);
            assertThat(sut.filter(req)).isSameAs(page);
        }
    }
}
