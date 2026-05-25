package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.repository.ScheduleRepository;
import com.example.tgs_dev.service.schedule.DurationResolver;
import com.example.tgs_dev.service.schedule.DurationResolverContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

/**
 * Unit tests for {@link ScheduleService}.
 *
 * <p>{@link DurationResolver} is mocked so that the orchestration logic (loop,
 * ordering, linking) can be verified independently from the actual resolver chain.
 * The resolver chain itself is tested in {@link com.example.tgs_dev.service.schedule}
 * package tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService – calculateVehicleSchedules")
class ScheduleServiceTest {

    @Mock ScheduleRepository              repo;
    @Mock TenantService                   tenantService;
    @Mock DurationResolver                durationResolver;
    @Mock RouteOperationalPeriodService   periodService;
    @Mock ScheduleTemplateVersionService  templateVersionService;

    ScheduleService sut;

    private static final Company COMPANY = company(1, "Test Corp");

    private RouteOperation    op;
    private VehicleAssignment va;

    @BeforeEach
    void setUp() {
        sut = new ScheduleService(repo, tenantService, durationResolver,
                                  periodService, templateVersionService);

        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
        lenient().when(durationResolver.resolve(any(DurationResolverContext.class))).thenReturn(30);
        // Default: period mirrors the route's own baseDuration/cycleCount so existing
        // assertions don't break when a test doesn't need period-specific overrides.
        // Default: mirrors the route's own baseDuration/cycleCount so existing assertions
        // don't break when a test doesn't need a period-specific override.
        // The null guard handles Mockito's internal stub-recording invocations where
        // argument matchers produce null placeholder values.
        lenient().when(periodService.findActiveForDateOrThrow(any(), any(), any()))
                 .thenAnswer(inv -> {
                     Route r = inv.getArgument(0);
                     if (r == null) return null;   // Mockito stub-recording artifact
                     RouteOperationalPeriod p = new RouteOperationalPeriod(
                             r, COMPANY, "Default", 30, 3,
                             LocalDate.of(2024, 1, 1), null);
                     p.setId(0);
                     return p;
                 });
        // Default: no active version → fall back to template defaults
        lenient().when(templateVersionService.findActiveForDate(any(), any(), any()))
                 .thenReturn(Optional.empty());

        Route route = route(1, "1");
        ScheduleTemplate template = template(100, route, LocalTime.of(6, 0));
        op = operation(1, route, OP_DATE);
        va = assignment(1, op, vehicle(10, "V-001"), template, 1);
    }

    @Test @DisplayName("generates exactly cycleCount schedules per assignment")
    void generatesExactlyCycleCountSchedules() {
        sut.calculateVehicleSchedules(List.of(va));
        assertThat(capturedSchedules()).hasSize(3);
    }

    @Test @DisplayName("first schedule has the template's start time")
    void firstScheduleStartsAtTemplateTime() {
        sut.calculateVehicleSchedules(List.of(va));
        assertThat(capturedSchedules().getFirst().getDepartureTime()).isEqualTo(LocalTime.of(6, 0));
    }

    @Test @DisplayName("each consecutive schedule uses the duration from the resolver")
    void schedulesIncrementByResolvedDuration() {
        // resolver always returns 30 (stubbed in setUp)
        sut.calculateVehicleSchedules(List.of(va));
        List<Schedule> schedules = capturedSchedules();
        assertThat(schedules.get(0).getDepartureTime()).isEqualTo(LocalTime.of(6,  0));
        assertThat(schedules.get(1).getDepartureTime()).isEqualTo(LocalTime.of(6, 30));
        assertThat(schedules.get(2).getDepartureTime()).isEqualTo(LocalTime.of(7,  0));
    }

    @Test @DisplayName("resolver is called once per cycle with the correct departure time")
    void resolverCalledOncePerCycle() {
        sut.calculateVehicleSchedules(List.of(va));
        // cycleCount = 3 → resolver called 3 times
        verify(durationResolver, times(3)).resolve(any(DurationResolverContext.class));
    }

    @Test @DisplayName("resolver context carries the operation's serviceDate")
    void resolverContextCarriesServiceDate() {
        sut.calculateVehicleSchedules(List.of(va));

        ArgumentCaptor<DurationResolverContext> captor =
                ArgumentCaptor.forClass(DurationResolverContext.class);
        verify(durationResolver, atLeastOnce()).resolve(captor.capture());

        captor.getAllValues()
                .forEach(ctx -> assertThat(ctx.operationDate()).isEqualTo(OP_DATE));
    }

    @Test @DisplayName("departureOrder is 1-based and sequential")
    void departureOrderIsOneBased() {
        sut.calculateVehicleSchedules(List.of(va));
        assertThat(capturedSchedules()).extracting(Schedule::getDepartureOrder)
                .containsExactly(1, 2, 3);
    }

    @Test @DisplayName("each schedule is linked to its assignment")
    void schedulesLinkedToAssignment() {
        sut.calculateVehicleSchedules(List.of(va));
        assertThat(capturedSchedules())
                .allSatisfy(s -> assertThat(s.getVehicleAssignment()).isEqualTo(va));
    }

    @Test @DisplayName("dynamic durations: resolver returns different values per call")
    void dynamicDurationsFromResolver() {
        // Simulate a transition: first 2 calls return 120, then 60
        when(durationResolver.resolve(any()))
                .thenReturn(120, 120, 60);

        Route            route    = route(2, "R2");
        ScheduleTemplate template = template(200, route, LocalTime.of(6, 30));
        RouteOperation   op2      = operation(2, route, OP_DATE);
        VehicleAssignment va2     = assignment(2, op2, vehicle(20, "V-002"), template, 1);

        sut.calculateVehicleSchedules(List.of(va2));

        List<Schedule> schedules = capturedSchedules();
        assertThat(schedules.get(0).getDepartureTime()).isEqualTo(LocalTime.of(6,  30));
        assertThat(schedules.get(1).getDepartureTime()).isEqualTo(LocalTime.of(8,  30)); // +120
        assertThat(schedules.get(2).getDepartureTime()).isEqualTo(LocalTime.of(10, 30)); // +120
    }

    @Nested @DisplayName("multiple assignments")
    class MultipleAssignments {

        @Test @DisplayName("total schedule count equals sum of all cycleCount values")
        void totalCountMatchesSumOfCycles() {
            Route route2      = route(2, "2");
            ScheduleTemplate t2  = template(200, route2, LocalTime.of(7, 0));
            VehicleAssignment va2 = assignment(2, op, vehicle(20, "V-002"), t2, 2);

            // route2 needs cycleCount=2 via period (default stub returns 3)
            when(periodService.findActiveForDateOrThrow(eq(route2), any(), any()))
                    .thenReturn(new RouteOperationalPeriod(
                            route2, COMPANY, "Default", 20, 2,
                            LocalDate.of(2024, 1, 1), null));

            sut.calculateVehicleSchedules(List.of(va, va2));

            // va → 3 schedules, va2 → 2 schedules
            assertThat(capturedSchedules()).hasSize(5);
        }

        @Test @DisplayName("schedules for each assignment start at their own template's start time")
        void eachAssignmentUsesItsOwnStartTime() {
            Route route2      = route(2, "2");
            ScheduleTemplate t2  = template(200, route2, LocalTime.of(8, 0));
            VehicleAssignment va2 = assignment(2, op, vehicle(20, "V-002"), t2, 2);

            sut.calculateVehicleSchedules(List.of(va, va2));

            List<Schedule> all    = capturedSchedules();
            List<Schedule> forVa  = all.stream().filter(s -> s.getVehicleAssignment().equals(va)).toList();
            List<Schedule> forVa2 = all.stream().filter(s -> s.getVehicleAssignment().equals(va2)).toList();

            assertThat(forVa.getFirst().getDepartureTime()).isEqualTo(LocalTime.of(6, 0));
            assertThat(forVa2.getFirst().getDepartureTime()).isEqualTo(LocalTime.of(8, 0));
        }
    }

    @Test @DisplayName("empty assignment list results in an empty saveAll call")
    void emptyList_savesNothing() {
        sut.calculateVehicleSchedules(List.of());
        assertThat(capturedSchedules()).isEmpty();
    }

    // ── RouteOperationalPeriod resolution ─────────────────────────────────────

    @Nested @DisplayName("RouteOperationalPeriod resolution")
    class PeriodResolution {

        private RouteOperationalPeriod fakePeriod(Route route, int baseDuration, int cycleCount) {
            RouteOperationalPeriod p = new RouteOperationalPeriod(
                    route, COMPANY, "Test Period", baseDuration, cycleCount,
                    LocalDate.of(2024, 1, 1), null);
            p.setId(99);
            return p;
        }

        @Test @DisplayName("uses period cycleCount when an active period is found")
        void activePeriod_usesPeriodCycleCount() {
            Route route = route(1, "1");
            ScheduleTemplate t = template(100, route, LocalTime.of(6, 0));
            RouteOperation o = operation(1, route, OP_DATE);
            VehicleAssignment v = assignment(1, o, vehicle(10, "V-001"), t, 1);

            // Period overrides cycleCount to 5
            when(periodService.findActiveForDateOrThrow(eq(route), any(), eq(OP_DATE)))
                    .thenReturn(fakePeriod(route, 30, 5));

            sut.calculateVehicleSchedules(List.of(v));

            assertThat(capturedSchedules()).hasSize(5);
        }

        @Test @DisplayName("uses period baseDuration in resolver context when active")
        void activePeriod_usesPeriodBaseDurationInContext() {
            Route route = route(1, "1");
            ScheduleTemplate t = template(100, route, LocalTime.of(6, 0));
            RouteOperation o = operation(1, route, OP_DATE);
            VehicleAssignment v = assignment(1, o, vehicle(10, "V-001"), t, 1);

            // Period overrides baseDuration to 60
            when(periodService.findActiveForDateOrThrow(eq(route), any(), eq(OP_DATE)))
                    .thenReturn(fakePeriod(route, 60, 2));

            sut.calculateVehicleSchedules(List.of(v));

            ArgumentCaptor<DurationResolverContext> captor =
                    ArgumentCaptor.forClass(DurationResolverContext.class);
            verify(durationResolver, atLeastOnce()).resolve(captor.capture());
            captor.getAllValues()
                    .forEach(ctx -> assertThat(ctx.effectiveBaseDuration()).isEqualTo(60));
        }

        @Test @DisplayName("throws BusinessException when no active period covers the operation date")
        void noPeriod_throws() {
            when(periodService.findActiveForDateOrThrow(any(), any(), any()))
                    .thenThrow(new BusinessException(
                            "validation.routeOperationalPeriod.noPeriodForDate|1|" + OP_DATE));

            List<VehicleAssignment> assignments = List.of(va);
            assertThatThrownBy(() -> sut.calculateVehicleSchedules(assignments))
                    .isInstanceOf(BusinessException.class);
            verify(repo, never()).saveAll(any());
        }

        @Test @DisplayName("period time ranges are carried into every resolver context")
        void activePeriod_timeRangesPassedToContext() {
            Route route = route(1, "1");
            ScheduleTemplate t = template(100, route, LocalTime.of(6, 0));
            RouteOperation o = operation(1, route, OP_DATE);
            VehicleAssignment v = assignment(1, o, vehicle(10, "V-001"), t, 1);

            RouteOperationalPeriod period = fakePeriod(route, 30, 2);
            period.setUseTimeRanges(true);
            OperationalPeriodTimeRange range = new OperationalPeriodTimeRange(
                    LocalTime.of(6, 0), LocalTime.of(12, 0), 45, 1, false);
            range.setPeriod(period);
            period.getTimeRanges().add(range);

            when(periodService.findActiveForDateOrThrow(eq(route), any(), eq(OP_DATE)))
                    .thenReturn(period);

            sut.calculateVehicleSchedules(List.of(v));

            ArgumentCaptor<DurationResolverContext> captor =
                    ArgumentCaptor.forClass(DurationResolverContext.class);
            verify(durationResolver, atLeastOnce()).resolve(captor.capture());
            captor.getAllValues().forEach(ctx -> {
                assertThat(ctx.effectiveTimeRanges()).hasSize(1);
                assertThat(ctx.effectiveTimeRanges().getFirst().durationMinutes()).isEqualTo(45);
            });
        }
    }

    // ── ScheduleTemplateVersion resolution ────────────────────────────────────

    @Nested @DisplayName("ScheduleTemplateVersion resolution")
    class TemplateVersionResolution {

        private ScheduleTemplateVersion fakeVersion(ScheduleTemplate t, LocalTime startTime) {
            ScheduleTemplateVersion v = new ScheduleTemplateVersion(
                    t, COMPANY, "Test Version", startTime,
                    LocalDate.of(2024, 1, 1), null);
            v.setId(99);
            return v;
        }

        @Test @DisplayName("first schedule uses version startTime when an active version is found")
        void activeVersion_usesVersionStartTime() {
            Route route = route(1, "1");
            ScheduleTemplate t = template(100, route, LocalTime.of(6, 0));  // template default
            RouteOperation o = operation(1, route, OP_DATE);
            VehicleAssignment v = assignment(1, o, vehicle(10, "V-001"), t, 1);

            // Version overrides startTime to 07:30
            when(templateVersionService.findActiveForDate(eq(t), any(), eq(OP_DATE)))
                    .thenReturn(Optional.of(fakeVersion(t, LocalTime.of(7, 30))));

            sut.calculateVehicleSchedules(List.of(v));

            assertThat(capturedSchedules().getFirst().getDepartureTime())
                    .isEqualTo(LocalTime.of(7, 30));
        }

        @Test @DisplayName("first schedule uses template startTime when no version is active")
        void noVersion_usesTemplateStartTime() {
            // templateVersionService already returns empty by default in setUp
            // va's template has startTime=06:00
            sut.calculateVehicleSchedules(List.of(va));

            assertThat(capturedSchedules().getFirst().getDepartureTime())
                    .isEqualTo(LocalTime.of(6, 0));
        }
    }

    // ── CRUD delegation ───────────────────────────────────────────────────────

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("delegates to repo.findAll")
        void delegates() {
            Schedule s = schedule(1, va, 1, LocalTime.of(6, 0));
            when(repo.findAll()).thenReturn(List.of(s));
            assertThat(sut.findAll()).containsExactly(s);
        }
    }

    @Nested @DisplayName("save (single)")
    class SaveSingle {
        @Test @DisplayName("delegates to repo.save and returns persisted entity")
        void delegates() {
            Schedule s = schedule(1, va, 1, LocalTime.of(6, 0));
            when(repo.save(s)).thenReturn(s);
            assertThat(sut.save(s)).isSameAs(s);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns Optional when found")
        void found() {
            Schedule s = schedule(1, va, 1, LocalTime.of(6, 0));
            when(repo.findById(1)).thenReturn(Optional.of(s));
            assertThat(sut.findById(1)).contains(s);
        }

        @Test @DisplayName("returns empty Optional when not found")
        void notFound() {
            when(repo.findById(99)).thenReturn(Optional.empty());
            assertThat(sut.findById(99)).isEmpty();
        }
    }

    @Nested @DisplayName("delete (single)")
    class DeleteSingle {
        @Test @DisplayName("delegates to repo.delete")
        void delegates() {
            Schedule s = schedule(1, va, 1, LocalTime.of(6, 0));
            sut.delete(s);
            verify(repo).delete(s);
        }
    }

    @Nested @DisplayName("findAllByAssignment")
    class FindAllByAssignment {
        @Test @DisplayName("delegates to repo with a Specification filtering by assignment ids")
        void delegates() {
            Schedule s = schedule(1, va, 1, LocalTime.of(6, 0));
            when(repo.findAll(any(Specification.class))).thenReturn(List.of(s));
            assertThat(sut.findAllByAssignment(List.of(1))).containsExactly(s);
        }
    }

    @Nested @DisplayName("findAllById")
    class FindAllById {
        @Test @DisplayName("delegates to repo.findAllById")
        void delegates() {
            Schedule s = schedule(1, va, 1, LocalTime.of(6, 0));
            when(repo.findAllById(List.of(1))).thenReturn(List.of(s));
            assertThat(sut.findAllById(List.of(1))).containsExactly(s);
        }
    }

    @Nested @DisplayName("saveAll (list)")
    class SaveAll {
        @Test @DisplayName("delegates to repo.saveAll and returns persisted list")
        void delegates() {
            Schedule s = schedule(1, va, 1, LocalTime.of(6, 0));
            when(repo.saveAll(List.of(s))).thenReturn(List.of(s));
            assertThat(sut.saveAll(List.of(s))).containsExactly(s);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Schedule> capturedSchedules() {
        ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        return captor.getValue();
    }
}
