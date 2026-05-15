package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for ScheduleService.
 * Only calculateVehicleSchedules is tested here because it contains real domain
 * logic (schedule generation algorithm). CRUD delegation methods are intentionally
 * omitted — they are wiring, not logic; an integration test is the right place for them.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService – calculateVehicleSchedules")
class ScheduleServiceTest {

    @Mock ScheduleRepository repo;
    @InjectMocks ScheduleService sut;

    private RouteOperation   op;
    private VehicleAssignment va;

    @BeforeEach
    void setUp() {
        /*baseDuration=*/
        /*cycleCount=*/
        Route route = route(1, "1", /*baseDuration=*/30, /*cycleCount=*/3);
        ScheduleTemplate template = template(100, route, LocalTime.of(6, 0));
        op       = operation(1, route, OP_DATE);
        va       = assignment(1, op, vehicle(10, "V-001"), template, 1);
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

    @Test @DisplayName("each consecutive schedule is baseDuration minutes after the previous")
    void schedulesIncrementByBaseDuration() {
        sut.calculateVehicleSchedules(List.of(va));
        List<Schedule> schedules = capturedSchedules();
        assertThat(schedules.get(0).getDepartureTime()).isEqualTo(LocalTime.of(6,  0));
        assertThat(schedules.get(1).getDepartureTime()).isEqualTo(LocalTime.of(6, 30));
        assertThat(schedules.get(2).getDepartureTime()).isEqualTo(LocalTime.of(7,  0));
    }

    @Test @DisplayName("departureOrder is 1-based and sequential")
    void departureOrderIsOneBased() {
        sut.calculateVehicleSchedules(List.of(va));
        List<Schedule> schedules = capturedSchedules();
        assertThat(schedules).extracting(Schedule::getDepartureOrder).containsExactly(1, 2, 3);
    }

    @Test @DisplayName("each schedule is linked to its assignment")
    void schedulesLinkedToAssignment() {
        sut.calculateVehicleSchedules(List.of(va));
        assertThat(capturedSchedules())
                .allSatisfy(s -> assertThat(s.getVehicleAssignment()).isEqualTo(va));
    }

    @Nested @DisplayName("multiple assignments")
    class MultipleAssignments {

        @Test @DisplayName("total schedule count equals sum of all cycleCount values")
        void totalCountMatchesSumOfCycles() {
            Route route2     = route(2, "2", 20, /*cycleCount=*/2);
            ScheduleTemplate t2 = template(200, route2, LocalTime.of(7, 0));
            VehicleAssignment va2 = assignment(2, op, vehicle(20, "V-002"), t2, 2);

            sut.calculateVehicleSchedules(List.of(va, va2));

            // va → 3 schedules, va2 → 2 schedules
            assertThat(capturedSchedules()).hasSize(5);
        }

        @Test @DisplayName("schedules for each assignment respect their own template's start time")
        void eachAssignmentUsesItsOwnStartTime() {
            Route route2     = route(2, "2", 15, 2);
            ScheduleTemplate t2 = template(200, route2, LocalTime.of(8, 0));
            VehicleAssignment va2 = assignment(2, op, vehicle(20, "V-002"), t2, 2);

            sut.calculateVehicleSchedules(List.of(va, va2));

            List<Schedule> all = capturedSchedules();
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

    // ── Helper ────────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private List<Schedule> capturedSchedules() {
        ArgumentCaptor<List<Schedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        return captor.getValue();
    }
}
