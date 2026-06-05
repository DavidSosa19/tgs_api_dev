package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.repository.RouteOperationRepository;
import com.example.tgs_dev.service.strategy.AssignmentSlot;
import com.example.tgs_dev.service.strategy.ScheduleInitStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.tgs_dev.TestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.clearInvocations;

/**
 * Unit tests for {@link OperationOrchestratorService}.
 *
 * <p>The strategy and {@link OperationInitializer} are injected as mocks.
 * Concrete strategy behaviour is tested in {@code RotationBasedStrategyTest};
 * persistence behaviour is tested in {@code OperationInitializerTest}.
 *
 * <p>The subject under test ({@code sut}) is constructed manually because the
 * constructor accepts {@code List<ScheduleInitStrategy>}, which Mockito's
 * {@code @InjectMocks} does not handle.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationOrchestratorService")
class OperationOrchestratorServiceTest {

    @Mock OperationInitializer     initializer;
    @Mock RouteService             routeService;
    @Mock RouteOperationRepository routeOperationRepository;
    @Mock TenantService            tenantService;
    @Mock ScheduleInitStrategy     strategy;

    OperationOrchestratorService sut;

    private Route   route1;
    private Route   route2;
    private Company company;

    @BeforeEach
    void setUp() {
        route1  = route(1, "1");
        route2  = route(2, "2");
        company = company(1, "Test Corp", SchedulingMode.ROTATION_BASED);

        lenient().when(strategy.mode()).thenReturn(SchedulingMode.ROTATION_BASED);
        lenient().when(tenantService.currentCompany()).thenReturn(company);
        lenient().when(tenantService.currentCompanyId()).thenReturn(1);
        lenient().when(routeOperationRepository.findRouteIdsWithActiveOperation(any(), any()))
                 .thenReturn(List.of());

        sut = new OperationOrchestratorService(
                initializer, routeService, routeOperationRepository,
                tenantService, List.of(strategy));

        // strategy.mode() is called during construction to populate the dispatch map.
        clearInvocations(strategy);
    }

    // ── initOperation ─────────────────────────────────────────────────────────

    @Nested @DisplayName("initOperation (single route)")
    class InitOperation {

        private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

        @Test @DisplayName("calls strategy.resolve and delegates persistence to OperationInitializer")
        void delegatesToInitializer() {
            List<AssignmentSlot> slots = List.<AssignmentSlot>of();
            when(strategy.resolve(route1, DATE)).thenReturn(slots);

            sut.initOperation(route1, DATE);

            verify(strategy).resolve(route1, DATE);
            verify(initializer).persistOne(route1, DATE, slots);
        }
    }

    // ── initAllOperations ─────────────────────────────────────────────────────

    @Nested @DisplayName("initAllOperations")
    class InitAllOperations {

        private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

        @Test @DisplayName("full success — every route initialised, no failures")
        void resolvesAllAndPersistsEach() {
            when(routeService.findAll()).thenReturn(List.of(route1, route2));
            when(strategy.resolveAll(List.of(route1, route2), DATE))
                    .thenReturn(slotsByRoute(List.<AssignmentSlot>of(), List.<AssignmentSlot>of()));

            InitOperationsResult result = sut.initAllOperations(DATE);

            assertThat(result.initialized()).isEqualTo(2);
            assertThat(result.skipped()).isZero();
            assertThat(result.failures()).isEmpty();
            assertThat(result.isFullSuccess()).isTrue();
            verify(strategy, times(1)).resolveAll(List.of(route1, route2), DATE);
            verify(strategy, never()).resolve(any(), any());
            verify(initializer).persistOne(eq(route1), eq(DATE), anyList());
            verify(initializer).persistOne(eq(route2), eq(DATE), anyList());
        }

        @Test @DisplayName("idempotency — skipped count reflects routes already initialised")
        void skipsRoutesAlreadyInitialised() {
            when(routeService.findAll()).thenReturn(List.of(route1, route2));
            when(routeOperationRepository.findRouteIdsWithActiveOperation(DATE, 1))
                    .thenReturn(List.of(route1.getId()));
            when(strategy.resolveAll(List.of(route2), DATE))
                    .thenReturn(slotsByRoute(route2, List.<AssignmentSlot>of()));

            InitOperationsResult result = sut.initAllOperations(DATE);

            assertThat(result.initialized()).isEqualTo(1);
            assertThat(result.skipped()).isEqualTo(1);
            assertThat(result.failures()).isEmpty();
            verify(initializer).persistOne(eq(route2), eq(DATE), anyList());
            verify(initializer, never()).persistOne(eq(route1), any(), any());
        }

        @Test @DisplayName("all routes already initialised → noop result with skipped count")
        void allInitialised_returnsNoop() {
            when(routeService.findAll()).thenReturn(List.of(route1, route2));
            when(routeOperationRepository.findRouteIdsWithActiveOperation(DATE, 1))
                    .thenReturn(List.of(route1.getId(), route2.getId()));

            InitOperationsResult result = sut.initAllOperations(DATE);

            assertThat(result.isNoop()).isTrue();
            assertThat(result.skipped()).isEqualTo(2);
            assertThat(result.initialized()).isZero();
            assertThat(result.failures()).isEmpty();
            verify(strategy, never()).resolveAll(any(), any());
            verifyNoInteractions(initializer);
        }

        @Test @DisplayName("empty route list short-circuits — noop with zero skipped")
        void emptyRoutes_shortCircuits() {
            when(routeService.findAll()).thenReturn(List.of());

            InitOperationsResult result = sut.initAllOperations(DATE);

            assertThat(result.isNoop()).isTrue();
            assertThat(result.skipped()).isZero();
            verify(strategy, never()).resolve(any(), any());
            verify(strategy, never()).resolveAll(any(), any());
            verifyNoInteractions(initializer);
        }

        @Test @DisplayName("partial success — failure on one route is captured with the reason")
        void partialSuccess_capturesFailureReason() {
            when(routeService.findAll()).thenReturn(List.of(route1, route2));
            when(strategy.resolveAll(List.of(route1, route2), DATE))
                    .thenReturn(slotsByRoute(List.<AssignmentSlot>of(), List.<AssignmentSlot>of()));

            doThrow(new BusinessException("validation.period.missingDepartureTimes|15"))
                    .when(initializer).persistOne(eq(route1), any(), any());

            InitOperationsResult result = sut.initAllOperations(DATE);

            assertThat(result.isPartial()).isTrue();
            assertThat(result.initialized()).isEqualTo(1);
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().getFirst().routeNumber()).isEqualTo("1");
            assertThat(result.failures().getFirst().reason())
                    .isEqualTo("validation.period.missingDepartureTimes|15");
        }

        @Test @DisplayName("all failed — every route fails, result reflects total failure")
        void allFailed_classifiedAsAllFailed() {
            when(routeService.findAll()).thenReturn(List.of(route1, route2));
            when(strategy.resolveAll(List.of(route1, route2), DATE))
                    .thenReturn(slotsByRoute(List.<AssignmentSlot>of(), List.<AssignmentSlot>of()));

            doThrow(new BusinessException("err.first"))
                    .when(initializer).persistOne(eq(route1), any(), any());
            doThrow(new RuntimeException("boom"))
                    .when(initializer).persistOne(eq(route2), any(), any());

            InitOperationsResult result = sut.initAllOperations(DATE);

            assertThat(result.isAllFailed()).isTrue();
            assertThat(result.initialized()).isZero();
            assertThat(result.failures()).hasSize(2);
            // BusinessException → i18n key surfaced verbatim
            assertThat(result.failures().get(0).reason()).isEqualTo("err.first");
            // RuntimeException → SimpleName: message fallback
            assertThat(result.failures().get(1).reason()).startsWith("RuntimeException: ");
        }

        /** Builds an order-preserving map from the route1/route2 captured in this scope. */
        @SafeVarargs
        private Map<Route, List<AssignmentSlot>> slotsByRoute(List<AssignmentSlot>... slotLists) {
            Map<Route, List<AssignmentSlot>> map = new LinkedHashMap<>();
            Route[] routes = { route1, route2 };
            for (int i = 0; i < slotLists.length; i++) {
                map.put(routes[i], slotLists[i]);
            }
            return map;
        }

        /** Single-route variant: explicit route + slot list. */
        private Map<Route, List<AssignmentSlot>> slotsByRoute(Route only, List<AssignmentSlot> slots) {
            Map<Route, List<AssignmentSlot>> map = new LinkedHashMap<>();
            map.put(only, slots);
            return map;
        }
    }

    // ── Strategy dispatch ─────────────────────────────────────────────────────

    @Nested @DisplayName("strategy dispatch")
    class StrategyDispatch {

        private static final LocalDate DATE = LocalDate.of(2024, 1, 15);

        @Test @DisplayName("dispatches to the strategy matching the company's scheduling mode")
        void dispatchesByCompanyMode() {
            when(strategy.resolve(route1, DATE)).thenReturn(List.of());

            sut.initOperation(route1, DATE);

            verify(strategy).resolve(route1, DATE);
        }

        @Test @DisplayName("throws IllegalStateException when no strategy is registered for the company's mode")
        void throwsWhenStrategyMissing() {
            OperationOrchestratorService empty = new OperationOrchestratorService(
                    initializer, routeService, routeOperationRepository,
                    tenantService, List.of());

            assertThatThrownBy(() -> empty.initOperation(route1, DATE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ROTATION_BASED")
                    .hasMessageContaining("ScheduleInitStrategy");
        }

        @Test @DisplayName("only the strategy whose mode() matches the company is invoked")
        void onlyMatchingStrategyInvoked() {
            ScheduleInitStrategy isolated = mock(ScheduleInitStrategy.class);
            when(isolated.mode()).thenReturn(SchedulingMode.ROTATION_BASED);
            when(isolated.resolve(route1, DATE)).thenReturn(List.of());

            OperationOrchestratorService sut2 = new OperationOrchestratorService(
                    initializer, routeService, routeOperationRepository,
                    tenantService, List.of(isolated));

            sut2.initOperation(route1, DATE);

            verify(isolated).resolve(route1, DATE);
            verifyNoInteractions(strategy);
        }
    }
}
