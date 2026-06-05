package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteCalendarOverrideRequest;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteCalendarOverride;
import com.example.tgs_dev.repository.RouteCalendarOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RouteCalendarOverrideService")
class RouteCalendarOverrideServiceTest {

    @Mock RouteCalendarOverrideRepository repo;
    @Mock TenantService                   tenantService;
    @Mock RouteService                    routeService;

    @InjectMocks RouteCalendarOverrideService sut;

    private static final int     COMPANY_ID = 1;
    private static final Company COMPANY    = company(COMPANY_ID, "ACME");
    private static final Route   ROUTE      = route(10, "R-10");
    private static final LocalDate DATE     = LocalDate.of(2024, 6, 15);

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
        lenient().when(routeService.findById(ROUTE.getId())).thenReturn(ROUTE);
    }

    // ── findByRouteAndDate ────────────────────────────────────────────────────

    @Nested @DisplayName("findByRouteAndDate")
    class FindByRouteAndDate {

        @Test @DisplayName("delegates to repo with tenant-scoped compound key")
        void delegates() {
            RouteCalendarOverride ov = new RouteCalendarOverride(ROUTE, COMPANY, DATE, false, 30);
            when(repo.findByRouteAndDateAndCompany(ROUTE.getId(), DATE, COMPANY_ID))
                    .thenReturn(Optional.of(ov));

            Optional<RouteCalendarOverride> result = sut.findByRouteAndDate(ROUTE, DATE);

            assertThat(result).contains(ov);
        }

        @Test @DisplayName("returns empty when no override exists for the date")
        void returnsEmpty() {
            when(repo.findByRouteAndDateAndCompany(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(sut.findByRouteAndDate(ROUTE, DATE)).isEmpty();
        }
    }

    // ── findAllByRoute ────────────────────────────────────────────────────────

    @Nested @DisplayName("findAllByRoute")
    class FindAllByRoute {

        @Test @DisplayName("resolves route via RouteService and returns tenant-scoped list")
        void returnsList() {
            RouteCalendarOverride ov = new RouteCalendarOverride(ROUTE, COMPANY, DATE, false, 30);
            when(repo.findAll(any(Specification.class))).thenReturn(List.of(ov));

            List<RouteCalendarOverride> result = sut.findAllByRoute(ROUTE.getId());

            assertThat(result).containsExactly(ov);
            verify(routeService).findById(ROUTE.getId());
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("findById")
    class FindById {

        @Test @DisplayName("returns override when it belongs to the tenant")
        void found() {
            RouteCalendarOverride ov = new RouteCalendarOverride(ROUTE, COMPANY, DATE, false, 30);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(ov));

            assertThat(sut.findById(1)).isSameAs(ov);
        }

        @Test @DisplayName("throws ResourceNotFoundException when not found or cross-tenant")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ── save (upsert) ─────────────────────────────────────────────────────────

    @Nested @DisplayName("save — upsert semantics")
    class Save {

        @Test @DisplayName("creates a new override when none exists for that date")
        void createsNew() {
            RouteCalendarOverrideRequest req = new RouteCalendarOverrideRequest(
                    DATE, false, 30, null);
            when(repo.findByRouteAndDateAndCompany(ROUTE.getId(), DATE, COMPANY_ID))
                    .thenReturn(Optional.empty());
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RouteCalendarOverride result = sut.save(ROUTE.getId(), req);

            assertThat(result.getOverrideDate()).isEqualTo(DATE);
            assertThat(result.getRoute()).isEqualTo(ROUTE);
            assertThat(result.getBaseDuration()).isEqualTo(30);
        }

        @Test @DisplayName("replaces an existing override for the same (route, date)")
        void replacesExisting() {
            RouteCalendarOverride existing = new RouteCalendarOverride(ROUTE, COMPANY, DATE, false, 20);
            when(repo.findByRouteAndDateAndCompany(ROUTE.getId(), DATE, COMPANY_ID))
                    .thenReturn(Optional.of(existing));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RouteCalendarOverrideRequest req = new RouteCalendarOverrideRequest(
                    DATE, false, 45, null);

            RouteCalendarOverride result = sut.save(ROUTE.getId(), req);

            assertThat(result).isSameAs(existing);
            assertThat(result.getBaseDuration()).isEqualTo(45);
        }

        @Test @DisplayName("populates ranges when useTimeRanges = true")
        void addsRangesWhenEnabled() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(LocalTime.of(6, 0), LocalTime.of(7, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(7, 0), LocalTime.of(8, 0), 30, 8, false)
            );
            RouteCalendarOverrideRequest req = new RouteCalendarOverrideRequest(
                    DATE, true, 30, ranges);
            when(repo.findByRouteAndDateAndCompany(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RouteCalendarOverride result = sut.save(ROUTE.getId(), req);

            assertThat(result.getRanges()).hasSize(2);
        }

        @Test @DisplayName("clears ranges when useTimeRanges = false even if ranges were present")
        void clearsRangesWhenDisabled() {
            RouteCalendarOverride existing = new RouteCalendarOverride(ROUTE, COMPANY, DATE, true, 30);
            // Simulate existing ranges via upsert
            when(repo.findByRouteAndDateAndCompany(any(), any(), any()))
                    .thenReturn(Optional.of(existing));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RouteCalendarOverrideRequest req = new RouteCalendarOverrideRequest(
                    DATE, false, 30, null);

            RouteCalendarOverride result = sut.save(ROUTE.getId(), req);

            assertThat(result.getRanges()).isEmpty();
        }

        @Test @DisplayName("overnight ranges are sorted to the end (crossesMidnight last)")
        void rangesAreSortedOvernightLast() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(LocalTime.of(22, 0), LocalTime.of(2, 0), 60, 8, true),  // overnight first in input
                    new RouteTimeRangeRequest(LocalTime.of(6, 0),  LocalTime.of(7, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(7, 0),  LocalTime.of(8, 0), 30, 8, false)
            );
            RouteCalendarOverrideRequest req = new RouteCalendarOverrideRequest(
                    DATE, true, 30, ranges);
            when(repo.findByRouteAndDateAndCompany(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RouteCalendarOverride result = sut.save(ROUTE.getId(), req);

            // overnight range must be last
            assertThat(result.getRanges().getLast().isCrossesMidnight()).isTrue();
        }

        @Test @DisplayName("sortOrder is assigned as 1-based sequential position")
        void sortOrderIsOneBased() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(LocalTime.of(6, 0), LocalTime.of(7, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(7, 0), LocalTime.of(8, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(8, 0), LocalTime.of(9, 0), 30, 8, false)
            );
            RouteCalendarOverrideRequest req = new RouteCalendarOverrideRequest(
                    DATE, true, 30, ranges);
            when(repo.findByRouteAndDateAndCompany(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RouteCalendarOverride result = sut.save(ROUTE.getId(), req);

            assertThat(result.getRanges()).extracting(r -> r.getSortOrder())
                    .containsExactly(1, 2, 3);
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete")
    class Delete {

        @Test @DisplayName("loads by id then delegates to repo.delete")
        void deletesFound() {
            RouteCalendarOverride ov = new RouteCalendarOverride(ROUTE, COMPANY, DATE, false, 30);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(ov));

            sut.delete(1);

            verify(repo).delete(ov);
        }

        @Test @DisplayName("throws when override not found")
        void throwsNotFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.delete(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
