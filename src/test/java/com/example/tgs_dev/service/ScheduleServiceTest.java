package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.repository.ScheduleRepository;
import com.example.tgs_dev.service.schedule.DepartureSlotGenerator;
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
 * Unit tests for {@link ScheduleService#calculateVehicleSchedules}.
 *
 * <h3>Design under test</h3>
 * The method generates a route-level slot sequence via {@link DepartureSlotGenerator}
 * and distributes those slots to vehicles in round-robin order, sorted by
 * {@code scheduleTemplate.sequenceOrder}.
 *
 * <h3>Mock strategy</h3>
 * {@link DepartureSlotGenerator} is mocked so that slot generation can be isolated
 * from headway-resolver behaviour (tested separately in resolver unit tests).
 * {@link RouteOperationalPeriodService} is mocked to control which period is active.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService – calculateVehicleSchedules")
class ScheduleServiceTest {

    @Mock ScheduleRepository            repo;
    @Mock TenantService                 tenantService;
    @Mock DepartureSlotGenerator        slotGenerator;
    @Mock RouteOperationalPeriodService periodService;

    ScheduleService sut;

    private static final Company   COMPANY = company(1, "Test Corp");
    private static final Route     ROUTE   = route(1, "R-1");
    private static final LocalDate OP_DATE = LocalDate.of(2024, 6, 15);

    // Default slots returned by mocked generator — 9 slots, 8-minute headway
    private static final List<LocalTime> NINE_SLOTS = List.of(
            LocalTime.of(6, 0), LocalTime.of(6, 8), LocalTime.of(6, 16),
            LocalTime.of(6, 24), LocalTime.of(6, 32), LocalTime.of(6, 40),
            LocalTime.of(6, 48), LocalTime.of(6, 56), LocalTime.of(7, 4));

    private RouteOperationalPeriod defaultPeriod;
    private RouteOperation         op;

    @BeforeEach
    void setUp() {
        sut = new ScheduleService(repo, tenantService, slotGenerator, periodService);

        lenient().when(tenantService.currentCompany()).thenReturn(COMPANY);
        lenient().when(tenantService.currentCompanyId()).thenReturn(1);

        defaultPeriod = operationalPeriod(1, ROUTE, 90, 8, LocalDate.of(2024, 1, 1), null);
        lenient().when(periodService.findActiveForDateOrThrow(any(), any(), any()))
                 .thenReturn(defaultPeriod);

        lenient().when(slotGenerator.generate(any(), any(), any()))
                 .thenReturn(NINE_SLOTS);

        op = operation(1, ROUTE, OP_DATE);
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test @DisplayName("empty assignment list → nothing saved, returns immediately")
    void emptyList_savesNothing() {
        sut.calculateVehicleSchedules(List.of());

        verify(repo, never()).saveAll(any());
        verify(slotGenerator, never()).generate(any(), any(), any());
    }

    // ── Slot count and global ordering ────────────────────────────────────────

    @Test @DisplayName("total schedules equals total slot count regardless of vehicle count")
    void totalSchedulesEqualsSlotCount() {
        ScheduleTemplate t1 = template(1, ROUTE, 1);
        ScheduleTemplate t2 = template(2, ROUTE, 2);
        ScheduleTemplate t3 = template(3, ROUTE, 3);

        List<VehicleAssignment> assignments = List.of(
                assignment(1, op, vehicle(10, "V-01"), t1, 1),
                assignment(2, op, vehicle(20, "V-02"), t2, 2),
                assignment(3, op, vehicle(30, "V-03"), t3, 3)
        );

        sut.calculateVehicleSchedules(assignments);

        // 9 slots → 9 schedules across 3 vehicles
        assertThat(capturedSchedules()).hasSize(NINE_SLOTS.size());
    }

    @Test @DisplayName("departureOrder is globally sequential (1-based) across all vehicles")
    void departureOrderIsGloballySequential() {
        ScheduleTemplate t1 = template(1, ROUTE, 1);
        ScheduleTemplate t2 = template(2, ROUTE, 2);

        List<VehicleAssignment> assignments = List.of(
                assignment(1, op, vehicle(10, "V-01"), t1, 1),
                assignment(2, op, vehicle(20, "V-02"), t2, 2)
        );

        sut.calculateVehicleSchedules(assignments);

        assertThat(capturedSchedules())
                .extracting(Schedule::getDepartureOrder)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test @DisplayName("departure times match the slots produced by the generator")
    void departureTimesMatchGeneratorSlots() {
        ScheduleTemplate t1 = template(1, ROUTE, 1);
        sut.calculateVehicleSchedules(List.of(assignment(1, op, vehicle(10, "V-01"), t1, 1)));

        assertThat(capturedSchedules())
                .extracting(Schedule::getDepartureTime)
                .containsExactlyElementsOf(NINE_SLOTS);
    }

    // ── Round-robin vehicle assignment ────────────────────────────────────────

    @Test @DisplayName("single vehicle receives all slots in order")
    void singleVehicle_getsAllSlots() {
        ScheduleTemplate t = template(1, ROUTE, 1);
        VehicleAssignment va = assignment(1, op, vehicle(10, "V-01"), t, 1);

        sut.calculateVehicleSchedules(List.of(va));

        assertThat(capturedSchedules())
                .allSatisfy(s -> assertThat(s.getVehicleAssignment()).isEqualTo(va));
    }

    @Test @DisplayName("3 vehicles receive slots in round-robin (V1→0,3,6 | V2→1,4,7 | V3→2,5,8)")
    void threeVehicles_roundRobinAssignment() {
        ScheduleTemplate t1 = template(1, ROUTE, 1);
        ScheduleTemplate t2 = template(2, ROUTE, 2);
        ScheduleTemplate t3 = template(3, ROUTE, 3);

        VehicleAssignment va1 = assignment(1, op, vehicle(10, "V-01"), t1, 1);
        VehicleAssignment va2 = assignment(2, op, vehicle(20, "V-02"), t2, 2);
        VehicleAssignment va3 = assignment(3, op, vehicle(30, "V-03"), t3, 3);

        sut.calculateVehicleSchedules(List.of(va1, va2, va3));

        List<Schedule> schedules = capturedSchedules();

        // Slot indices 0,3,6 → Vehicle 1
        assertThat(schedules.get(0).getVehicleAssignment()).isEqualTo(va1);
        assertThat(schedules.get(3).getVehicleAssignment()).isEqualTo(va1);
        assertThat(schedules.get(6).getVehicleAssignment()).isEqualTo(va1);

        // Slot indices 1,4,7 → Vehicle 2
        assertThat(schedules.get(1).getVehicleAssignment()).isEqualTo(va2);
        assertThat(schedules.get(4).getVehicleAssignment()).isEqualTo(va2);
        assertThat(schedules.get(7).getVehicleAssignment()).isEqualTo(va2);

        // Slot indices 2,5,8 → Vehicle 3
        assertThat(schedules.get(2).getVehicleAssignment()).isEqualTo(va3);
        assertThat(schedules.get(5).getVehicleAssignment()).isEqualTo(va3);
        assertThat(schedules.get(8).getVehicleAssignment()).isEqualTo(va3);
    }

    @Test @DisplayName("tripNumber is per-vehicle 1-based; departureOrder is global 1-based")
    void tripNumberPerVehicle_departureOrderGlobal() {
        ScheduleTemplate t1 = template(1, ROUTE, 1);
        ScheduleTemplate t2 = template(2, ROUTE, 2);
        ScheduleTemplate t3 = template(3, ROUTE, 3);

        VehicleAssignment va1 = assignment(1, op, vehicle(10, "V-01"), t1, 1);
        VehicleAssignment va2 = assignment(2, op, vehicle(20, "V-02"), t2, 2);
        VehicleAssignment va3 = assignment(3, op, vehicle(30, "V-03"), t3, 3);

        sut.calculateVehicleSchedules(List.of(va1, va2, va3));

        List<Schedule> schedules = capturedSchedules();

        // departureOrder is global (1..9 across vehicles)
        assertThat(schedules).extracting(Schedule::getDepartureOrder)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);

        // tripNumber is per-vehicle: vehicle assigned to slot k%3 has trips
        // slot 0,3,6 → trip 1, 2, 3 (vehicle 1)
        // slot 1,4,7 → trip 1, 2, 3 (vehicle 2)
        // slot 2,5,8 → trip 1, 2, 3 (vehicle 3)
        // In the global departureOrder sequence the tripNumber pattern is therefore:
        // 1,1,1, 2,2,2, 3,3,3
        assertThat(schedules).extracting(Schedule::getTripNumber)
                .containsExactly(1, 1, 1, 2, 2, 2, 3, 3, 3);

        // Sanity: trip 1 of vehicle 1 is at slot 0; trip 2 of vehicle 1 is at slot 3.
        assertThat(schedules.get(0).getTripNumber()).isEqualTo(1);
        assertThat(schedules.get(3).getTripNumber()).isEqualTo(2);
        assertThat(schedules.get(6).getTripNumber()).isEqualTo(3);
    }

    // ── Vehicle order driven by sequenceOrder ─────────────────────────────────

    @Test @DisplayName("vehicles sorted by sequenceOrder ASC, not by insertion order")
    void vehiclesSortedBySequenceOrder() {
        // Provide assignments in reverse sequence order — they should be sorted before assignment
        ScheduleTemplate t3 = template(3, ROUTE, 3);
        ScheduleTemplate t1 = template(1, ROUTE, 1);
        ScheduleTemplate t2 = template(2, ROUTE, 2);

        VehicleAssignment va3 = assignment(3, op, vehicle(30, "V-03"), t3, 3);
        VehicleAssignment va1 = assignment(1, op, vehicle(10, "V-01"), t1, 1);
        VehicleAssignment va2 = assignment(2, op, vehicle(20, "V-02"), t2, 2);

        // Pass in scrambled order: t3, t1, t2
        sut.calculateVehicleSchedules(List.of(va3, va1, va2));

        List<Schedule> schedules = capturedSchedules();

        // After sorting by sequenceOrder (1,2,3), slot 0 goes to va1 (order=1)
        assertThat(schedules.get(0).getVehicleAssignment()).isEqualTo(va1);
        assertThat(schedules.get(1).getVehicleAssignment()).isEqualTo(va2);
        assertThat(schedules.get(2).getVehicleAssignment()).isEqualTo(va3);
    }

    // ── Generator and period interaction ─────────────────────────────────────

    @Test @DisplayName("generator is called with the active period, route, and operation date")
    void generatorCalledWithCorrectArguments() {
        ScheduleTemplate t = template(1, ROUTE, 1);
        sut.calculateVehicleSchedules(List.of(assignment(1, op, vehicle(10, "V-01"), t, 1)));

        verify(slotGenerator).generate(defaultPeriod, ROUTE, OP_DATE);
    }

    @Test @DisplayName("period is resolved once per call regardless of vehicle count")
    void periodResolvedOnce() {
        ScheduleTemplate t1 = template(1, ROUTE, 1);
        ScheduleTemplate t2 = template(2, ROUTE, 2);

        sut.calculateVehicleSchedules(List.of(
                assignment(1, op, vehicle(10, "V-01"), t1, 1),
                assignment(2, op, vehicle(20, "V-02"), t2, 2)
        ));

        verify(periodService, times(1)).findActiveForDateOrThrow(any(), any(), any());
    }

    @Test @DisplayName("throws BusinessException when no active period covers the operation date")
    void noPeriod_throws() {
        when(periodService.findActiveForDateOrThrow(any(), any(), any()))
                .thenThrow(new BusinessException(
                        "validation.routeOperationalPeriod.noPeriodForDate|1|" + OP_DATE));

        ScheduleTemplate t = template(1, ROUTE, 1);
        List<VehicleAssignment> assignments = List.of(
                assignment(1, op, vehicle(10, "V-01"), t, 1));

        assertThatThrownBy(() -> sut.calculateVehicleSchedules(assignments))
                .isInstanceOf(BusinessException.class);

        verify(repo, never()).saveAll(any());
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Test @DisplayName("all schedules are persisted in a single saveAll call")
    void persistsInSingleBatch() {
        ScheduleTemplate t = template(1, ROUTE, 1);
        sut.calculateVehicleSchedules(List.of(assignment(1, op, vehicle(10, "V-01"), t, 1)));

        verify(repo, times(1)).saveAll(anyList());
    }

    @Test @DisplayName("each schedule is linked to the correct company")
    void schedulesLinkedToCompany() {
        ScheduleTemplate t = template(1, ROUTE, 1);
        sut.calculateVehicleSchedules(List.of(assignment(1, op, vehicle(10, "V-01"), t, 1)));

        assertThat(capturedSchedules())
                .allSatisfy(s -> assertThat(s.getCompany()).isEqualTo(COMPANY));
    }

    // ── CRUD delegation ───────────────────────────────────────────────────────

    @Nested @DisplayName("findAll")
    class FindAll {
        @Test @DisplayName("delegates to repo.findAll")
        void delegates() {
            ScheduleTemplate t = template(1, ROUTE, 1);
            Schedule s = schedule(1, assignment(1, op, vehicle(10, "V-01"), t, 1), 1, LocalTime.of(6, 0));
            when(repo.findAll()).thenReturn(List.of(s));
            assertThat(sut.findAll()).containsExactly(s);
        }
    }

    @Nested @DisplayName("findById")
    class FindById {
        @Test @DisplayName("returns Optional when found")
        void found() {
            ScheduleTemplate t = template(1, ROUTE, 1);
            Schedule s = schedule(1, assignment(1, op, vehicle(10, "V-01"), t, 1), 1, LocalTime.of(6, 0));
            when(repo.findById(1)).thenReturn(Optional.of(s));
            assertThat(sut.findById(1)).contains(s);
        }

        @Test @DisplayName("returns empty Optional when not found")
        void notFound() {
            when(repo.findById(99)).thenReturn(Optional.empty());
            assertThat(sut.findById(99)).isEmpty();
        }
    }

    @Nested @DisplayName("findAllByAssignment")
    class FindAllByAssignment {
        @Test @DisplayName("delegates to repo with a Specification")
        void delegates() {
            ScheduleTemplate t = template(1, ROUTE, 1);
            Schedule s = schedule(1, assignment(1, op, vehicle(10, "V-01"), t, 1), 1, LocalTime.of(6, 0));
            when(repo.findAll(any(Specification.class))).thenReturn(List.of(s));
            assertThat(sut.findAllByAssignment(List.of(1))).containsExactly(s);
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
