package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ConflictException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteOperationalPeriodRequest;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import com.example.tgs_dev.repository.RouteOperationalPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("RouteOperationalPeriodService")
class RouteOperationalPeriodServiceTest {

    @Mock RouteOperationalPeriodRepository repository;
    @Mock RouteService                     routeService;
    @Mock TenantService                    tenantService;

    RouteOperationalPeriodService sut;

    private static final Company         COMPANY    = company(1, "Corp");
    private static final Route           ROUTE      = route(10, "R-10");
    private static final LocalDate       FROM       = LocalDate.of(2024, 1, 15);
    private static final LocalDate       TO         = LocalDate.of(2024, 11, 29);

    @BeforeEach
    void setUp() {
        sut = new RouteOperationalPeriodService(repository, routeService, tenantService);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
        lenient().when(tenantService.currentCompanyId()).thenReturn(1);
        lenient().when(routeService.findById(10)).thenReturn(ROUTE);
    }

    // ── findAllByRoute ────────────────────────────────────────────────────────

    @Nested @DisplayName("findAllByRoute")
    class FindAllByRoute {

        @Test @DisplayName("delegates to repository with route and companyId")
        void delegates() {
            RouteOperationalPeriod p = operationalPeriod(1, ROUTE, 30, 3, FROM, TO);
            when(repository.findAllByRouteAndCompany(ROUTE, 1)).thenReturn(List.of(p));

            assertThat(sut.findAllByRoute(10)).containsExactly(p);
        }

        @Test @DisplayName("returns empty list when route has no periods")
        void empty() {
            when(repository.findAllByRouteAndCompany(ROUTE, 1)).thenReturn(List.of());
            assertThat(sut.findAllByRoute(10)).isEmpty();
        }
    }

    // ── findActiveForDate ─────────────────────────────────────────────────────

    @Nested @DisplayName("findActiveForDate")
    class FindActiveForDate {

        @Test @DisplayName("returns period from repository when found")
        void found() {
            RouteOperationalPeriod p = operationalPeriod(1, ROUTE, 30, 3, FROM, TO);
            when(repository.findActiveForDate(ROUTE, 1, FROM)).thenReturn(Optional.of(p));

            assertThat(sut.findActiveForDate(ROUTE, 1, FROM)).contains(p);
        }

        @Test @DisplayName("returns empty when no period covers the date")
        void notFound() {
            when(repository.findActiveForDate(ROUTE, 1, FROM)).thenReturn(Optional.empty());
            assertThat(sut.findActiveForDate(ROUTE, 1, FROM)).isEmpty();
        }
    }

    // ── findActiveForDateOrThrow ──────────────────────────────────────────────

    @Nested @DisplayName("findActiveForDateOrThrow")
    class FindActiveForDateOrThrow {

        @Test @DisplayName("returns period when found")
        void found() {
            RouteOperationalPeriod p = operationalPeriod(1, ROUTE, 30, 3, FROM, TO);
            when(repository.findActiveForDate(ROUTE, 1, FROM)).thenReturn(Optional.of(p));

            assertThat(sut.findActiveForDateOrThrow(ROUTE, 1, FROM)).isEqualTo(p);
        }

        @Test @DisplayName("throws BusinessException when no period covers the date")
        void notFound_throws() {
            when(repository.findActiveForDate(ROUTE, 1, FROM)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.findActiveForDateOrThrow(ROUTE, 1, FROM))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("noPeriodForDate");
        }
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create")
    class Create {

        private RouteOperationalPeriodRequest req(LocalDate from, LocalDate to) {
            return new RouteOperationalPeriodRequest("Año escolar", 30, 12, from, to,
                                                     false, null);
        }

        @Test @DisplayName("persists and returns the new period when no overlap")
        void success() {
            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), any(), eq(-1)))
                    .thenReturn(List.of());
            RouteOperationalPeriod saved = operationalPeriod(1, ROUTE, 30, 12, FROM, TO);
            when(repository.save(any())).thenReturn(saved);

            RouteOperationalPeriod result = sut.create(10, req(FROM, TO));

            assertThat(result).isEqualTo(saved);
            verify(repository).save(any(RouteOperationalPeriod.class));
        }

        @Test @DisplayName("throws ConflictException when date range overlaps existing period")
        void overlap_throws() {
            RouteOperationalPeriod existing = operationalPeriod(5, ROUTE, 30, 3, FROM, TO);
            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), any(), eq(-1)))
                    .thenReturn(List.of(existing));

            RouteOperationalPeriodRequest request = req(FROM, TO);
            assertThatThrownBy(() -> sut.create(10, request))
                    .isInstanceOf(ConflictException.class);
            verify(repository, never()).save(any());
        }

        @Test @DisplayName("throws BusinessException when effectiveTo is before effectiveFrom")
        void invalidDateRange_throws() {
            RouteOperationalPeriodRequest request = req(TO, FROM);   // TO < FROM
            assertThatThrownBy(() -> sut.create(10, request))
                    .isInstanceOf(BusinessException.class);
            verify(repository, never()).save(any());
        }

        @Test @DisplayName("allows open-ended period (effectiveTo = null)")
        void openEnded_success() {
            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), isNull(), eq(-1)))
                    .thenReturn(List.of());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> sut.create(10, req(FROM, null))).doesNotThrowAnyException();
        }

        @Test @DisplayName("persists time ranges when useTimeRanges is true")
        void withTimeRanges_success() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(
                            LocalTime.of(6, 0), LocalTime.of(12, 0),
                            30, false),
                    new RouteTimeRangeRequest(
                            LocalTime.of(12, 0), LocalTime.of(18, 0),
                            45, false)
            );
            RouteOperationalPeriodRequest request =
                    new RouteOperationalPeriodRequest("Vacaciones", 30, 9, FROM, TO, true, ranges);

            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), any(), eq(-1)))
                    .thenReturn(List.of());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RouteOperationalPeriod result = sut.create(10, request);

            assertThat(result.isUseTimeRanges()).isTrue();
            assertThat(result.getTimeRanges()).hasSize(2);
            assertThat(result.getTimeRanges().get(0).getSortOrder()).isEqualTo(1);
            assertThat(result.getTimeRanges().get(1).getSortOrder()).isEqualTo(2);
        }

        @Test @DisplayName("throws BusinessException when useTimeRanges=true but list has only one entry")
        void withTimeRanges_tooFew_throws() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(
                            LocalTime.of(6, 0), LocalTime.of(12, 0),
                            30, false)
            );
            RouteOperationalPeriodRequest request =
                    new RouteOperationalPeriodRequest("Vacaciones", 30, 9, FROM, TO, true, ranges);

            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), any(), eq(-1)))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> sut.create(10, request))
                    .isInstanceOf(BusinessException.class);
            verify(repository, never()).save(any());
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {

        private RouteOperationalPeriodRequest req() {
            return new RouteOperationalPeriodRequest("Vacaciones", 40, 9, FROM, TO,
                                                     false, null);
        }

        @Test @DisplayName("updates fields and saves when no overlap (excluding self)")
        void success() {
            RouteOperationalPeriod existing = operationalPeriod(1, ROUTE, 30, 3, FROM, TO);
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), any(), eq(1)))
                    .thenReturn(List.of());
            when(repository.save(existing)).thenReturn(existing);

            RouteOperationalPeriod result = sut.update(10, 1, req());

            assertThat(result.getLabel()).isEqualTo("Vacaciones");
            assertThat(result.getBaseDuration()).isEqualTo(40);
            assertThat(result.getCycleCount()).isEqualTo(9);
        }

        @Test @DisplayName("throws ResourceNotFoundException when period not found")
        void notFound_throws() {
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            RouteOperationalPeriodRequest request = req();
            assertThatThrownBy(() -> sut.update(10, 99, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("throws ConflictException when updated range overlaps another period")
        void overlap_throws() {
            RouteOperationalPeriod existing = operationalPeriod(1, ROUTE, 30, 3, FROM, TO);
            RouteOperationalPeriod other    = operationalPeriod(2, ROUTE, 25, 4, FROM, TO);
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), any(), eq(1)))
                    .thenReturn(List.of(other));

            RouteOperationalPeriodRequest request = req();
            assertThatThrownBy(() -> sut.update(10, 1, request))
                    .isInstanceOf(ConflictException.class);
        }

        @Test @DisplayName("replaces time ranges atomically when switching to useTimeRanges=true")
        void switchToTimeRanges_replacesCollection() {
            RouteOperationalPeriod existing = operationalPeriod(1, ROUTE, 30, 3, FROM, TO);
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(repository.findOverlapping(eq(ROUTE), eq(1), any(), any(), eq(1)))
                    .thenReturn(List.of());
            when(repository.save(existing)).thenReturn(existing);

            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(
                            LocalTime.of(6, 0), LocalTime.of(12, 0),
                            30, false),
                    new RouteTimeRangeRequest(
                            LocalTime.of(12, 0), LocalTime.of(18, 0),
                            45, false)
            );
            RouteOperationalPeriodRequest request =
                    new RouteOperationalPeriodRequest("Vacaciones", 40, 9, FROM, TO, true, ranges);

            RouteOperationalPeriod result = sut.update(10, 1, request);

            assertThat(result.isUseTimeRanges()).isTrue();
            assertThat(result.getTimeRanges()).hasSize(2);
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete")
    class Delete {

        @Test @DisplayName("soft-deletes the period when found")
        void success() {
            RouteOperationalPeriod p = operationalPeriod(1, ROUTE, 30, 3, FROM, TO);
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.of(p));

            sut.delete(10, 1);

            verify(repository).softDelete(p);
        }

        @Test @DisplayName("throws ResourceNotFoundException when period not found")
        void notFound_throws() {
            when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.delete(10, 99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
