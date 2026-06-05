package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.request.RouteTimeRangeRequest;
import com.example.tgs_dev.controller.request.SeasonalPatternRequest;
import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.SeasonalPattern;
import com.example.tgs_dev.repository.SeasonalPatternRepository;
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
@DisplayName("SeasonalPatternService")
class SeasonalPatternServiceTest {

    @Mock SeasonalPatternRepository repo;
    @Mock TenantService             tenantService;
    @Mock RouteService              routeService;

    @InjectMocks SeasonalPatternService sut;

    private static final int     COMPANY_ID = 1;
    private static final Company COMPANY    = company(COMPANY_ID, "ACME");
    private static final Route   ROUTE      = route(10, "R-10");
    private static final LocalDate FROM     = LocalDate.of(2024, 6,  1);
    private static final LocalDate TO       = LocalDate.of(2024, 8, 31);
    private static final LocalDate DATE     = LocalDate.of(2024, 7, 15);

    @BeforeEach
    void stubTenant() {
        lenient().when(tenantService.currentCompanyId()).thenReturn(COMPANY_ID);
        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
        lenient().when(routeService.findById(ROUTE.getId())).thenReturn(ROUTE);
    }

    // ── findActivePatternForDate ──────────────────────────────────────────────

    @Nested @DisplayName("findActivePatternForDate")
    class FindActivePatternForDate {

        @Test @DisplayName("delegates to repo using tenant companyId and returns result")
        void delegates() {
            SeasonalPattern pattern = new SeasonalPattern(ROUTE, COMPANY, "Summer", FROM, TO, false, 30);
            when(repo.findFirstActiveForDate(ROUTE.getId(), COMPANY_ID, DATE))
                    .thenReturn(Optional.of(pattern));

            Optional<SeasonalPattern> result = sut.findActivePatternForDate(ROUTE, DATE);

            assertThat(result).contains(pattern);
        }

        @Test @DisplayName("returns empty when no active pattern covers the date")
        void returnsEmpty() {
            when(repo.findFirstActiveForDate(any(), any(), any())).thenReturn(Optional.empty());

            assertThat(sut.findActivePatternForDate(ROUTE, DATE)).isEmpty();
        }
    }

    // ── findAllByRoute ────────────────────────────────────────────────────────

    @Nested @DisplayName("findAllByRoute")
    class FindAllByRoute {

        @Test @DisplayName("resolves route and returns tenant-scoped list")
        void returnsList() {
            SeasonalPattern p = new SeasonalPattern(ROUTE, COMPANY, "Summer", FROM, TO, false, 30);
            when(repo.findAllByRouteAndCompany(ROUTE.getId(), COMPANY_ID)).thenReturn(List.of(p));

            List<SeasonalPattern> result = sut.findAllByRoute(ROUTE.getId());

            assertThat(result).containsExactly(p);
            verify(routeService).findById(ROUTE.getId());
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("findById")
    class FindById {

        @Test @DisplayName("returns pattern when it belongs to the tenant")
        void found() {
            SeasonalPattern p = new SeasonalPattern(ROUTE, COMPANY, "Summer", FROM, TO, false, 30);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(p));

            assertThat(sut.findById(1)).isSameAs(p);
        }

        @Test @DisplayName("throws ResourceNotFoundException when not found or cross-tenant")
        void notFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.findById(99))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested @DisplayName("save")
    class Save {

        @Test @DisplayName("creates pattern with correct fields when date order is valid")
        void createsWithCorrectFields() {
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Summer 2024", FROM, TO, false, 45, null);
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SeasonalPattern result = sut.save(ROUTE.getId(), req);

            assertThat(result.getName()).isEqualTo("Summer 2024");
            assertThat(result.getSeasonFrom()).isEqualTo(FROM);
            assertThat(result.getSeasonTo()).isEqualTo(TO);
            assertThat(result.getBaseDuration()).isEqualTo(45);
            assertThat(result.getRoute()).isEqualTo(ROUTE);
            assertThat(result.getCompany()).isEqualTo(COMPANY);
        }

        @Test @DisplayName("throws BusinessException when seasonTo is before seasonFrom")
        void throwsOnInvalidDateOrder() {
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Bad", TO, FROM, false, 30, null);  // TO before FROM
            int routeId = ROUTE.getId();
            assertThatThrownBy(() -> sut.save(routeId, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("seasonalPattern.dateOrderInvalid");
        }

        @Test @DisplayName("does not add ranges when useTimeRanges = false")
        void noRangesWhenDisabled() {
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Summer", FROM, TO, false, 30, null);
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SeasonalPattern result = sut.save(ROUTE.getId(), req);

            assertThat(result.getRanges()).isEmpty();
        }

        @Test @DisplayName("populates ranges when useTimeRanges = true")
        void addsRangesWhenEnabled() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(LocalTime.of(6, 0), LocalTime.of(7, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(7, 0), LocalTime.of(8, 0), 30, 8, false)
            );
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Summer", FROM, TO, true, 30, ranges);
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SeasonalPattern result = sut.save(ROUTE.getId(), req);

            assertThat(result.getRanges()).hasSize(2);
        }

        @Test @DisplayName("overnight ranges are sorted to the end (crossesMidnight last)")
        void overnightRangeSortedLast() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(LocalTime.of(22, 0), LocalTime.of(2, 0), 60, 8, true),  // overnight first in input
                    new RouteTimeRangeRequest(LocalTime.of(6, 0),  LocalTime.of(7, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(7, 0),  LocalTime.of(8, 0), 30, 8, false)
            );
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Summer", FROM, TO, true, 30, ranges);
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SeasonalPattern result = sut.save(ROUTE.getId(), req);

            assertThat(result.getRanges().getLast().isCrossesMidnight()).isTrue();
        }

        @Test @DisplayName("sortOrder is assigned as 1-based sequential position")
        void sortOrderIsOneBased() {
            List<RouteTimeRangeRequest> ranges = List.of(
                    new RouteTimeRangeRequest(LocalTime.of(6, 0), LocalTime.of(7, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(7, 0), LocalTime.of(8, 0), 30, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(8, 0), LocalTime.of(9, 0), 30, 8, false)
            );
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Summer", FROM, TO, true, 30, ranges);
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SeasonalPattern result = sut.save(ROUTE.getId(), req);

            assertThat(result.getRanges()).extracting(r -> r.getSortOrder())
                    .containsExactly(1, 2, 3);
        }
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update")
    class Update {

        @Test @DisplayName("updates fields on the existing entity")
        void updatesFields() {
            SeasonalPattern existing = new SeasonalPattern(ROUTE, COMPANY, "Old", FROM, TO, false, 20);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LocalDate newFrom = LocalDate.of(2024, 9, 1);
            LocalDate newTo   = LocalDate.of(2024, 11, 30);
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Autumn", newFrom, newTo, false, 60, null);

            SeasonalPattern result = sut.update(1, req);

            assertThat(result).isSameAs(existing);
            assertThat(result.getName()).isEqualTo("Autumn");
            assertThat(result.getSeasonFrom()).isEqualTo(newFrom);
            assertThat(result.getSeasonTo()).isEqualTo(newTo);
            assertThat(result.getBaseDuration()).isEqualTo(60);
        }

        @Test @DisplayName("throws when date order is invalid on update")
        void throwsOnInvalidDateOrder() {
            SeasonalPattern existing = new SeasonalPattern(ROUTE, COMPANY, "Old", FROM, TO, false, 20);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(existing));

            SeasonalPatternRequest req = new SeasonalPatternRequest("Bad", TO, FROM, false, 30, null);

            assertThatThrownBy(() -> sut.update(1, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("seasonalPattern.dateOrderInvalid");
        }

        @Test @DisplayName("clears old ranges and repopulates when useTimeRanges = true")
        void replacesRanges() {
            SeasonalPattern existing = new SeasonalPattern(ROUTE, COMPANY, "Old", FROM, TO, true, 30);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<RouteTimeRangeRequest> newRanges = List.of(
                    new RouteTimeRangeRequest(LocalTime.of(6, 0), LocalTime.of(7, 0), 40, 8, false),
                    new RouteTimeRangeRequest(LocalTime.of(7, 0), LocalTime.of(8, 0), 40, 8, false)
            );
            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Summer", FROM, TO, true, 30, newRanges);

            SeasonalPattern result = sut.update(1, req);

            assertThat(result.getRanges()).hasSize(2);
            assertThat(result.getRanges().getFirst().getDurationMinutes()).isEqualTo(40);
        }

        @Test @DisplayName("clears ranges when useTimeRanges switches to false")
        void clearsRangesWhenDisabled() {
            SeasonalPattern existing = new SeasonalPattern(ROUTE, COMPANY, "Old", FROM, TO, true, 30);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(existing));
            when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SeasonalPatternRequest req = new SeasonalPatternRequest(
                    "Summer", FROM, TO, false, 30, null);

            SeasonalPattern result = sut.update(1, req);

            assertThat(result.getRanges()).isEmpty();
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete")
    class Delete {

        @Test @DisplayName("loads by id then delegates to repo.delete")
        void deletesFound() {
            SeasonalPattern p = new SeasonalPattern(ROUTE, COMPANY, "Summer", FROM, TO, false, 30);
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(p));

            sut.delete(1);

            verify(repo).delete(p);
        }

        @Test @DisplayName("throws ResourceNotFoundException when not found")
        void throwsNotFound() {
            when(repo.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.delete(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
