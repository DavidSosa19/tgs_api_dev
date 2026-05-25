package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.controller.response.RouteOperationDTO;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.service.OperationOrchestratorService;
import com.example.tgs_dev.service.RouteOperationService;
import com.example.tgs_dev.service.RouteService;
import com.example.tgs_dev.service.VehicleRemovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static com.example.tgs_dev.TestFixtures.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for OperationController.
 *
 * Spring Boot 4 removed @WebMvcTest — replaced with standaloneSetup() which
 * exercises the same HTTP contract (status codes, JSON shape, validation,
 * exception mapping) without requiring a Spring application context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperationController")
class OperationControllerTest {

    @Mock OperationOrchestratorService orchestratorService;
    @Mock RouteOperationService        routeOperationService;
    @Mock VehicleRemovalService        vehicleRemovalService;
    @Mock RouteService                 routeService;
    @Mock ConstraintMessageResolver    constraintResolver;

    MockMvc mockMvc;

    private static final String BASE = "/api/routeOperation";

    @BeforeEach
    void setUp() {
        OperationController controller = new OperationController(
                orchestratorService, routeOperationService, vehicleRemovalService, routeService);

        GlobalExceptionHandler exHandler = new GlobalExceptionHandler(constraintResolver);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        // JacksonJsonHttpMessageConverter uses Jackson 3 (tools.jackson) which has
        // Java time support built-in — no separate module needed.
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(exHandler)
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    // ── POST / ────────────────────────────────────────────────────────────────
    @Test @DisplayName("POST / → 201 with initialized message key")
    void create_returns201() throws Exception {
        Route route = route(1, "1");
        when(routeService.findById(1)).thenReturn(route);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"routeId":1,"date":"2024-01-15"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("routes.initialized.success"));

        verify(orchestratorService).initOperation(route, LocalDate.of(2024, 1, 15));
    }

    @Test @DisplayName("POST / with unknown routeId → 404")
    void create_unknownRoute_returns404() throws Exception {
        when(routeService.findById(99))
                .thenThrow(new ResourceNotFoundException("notFound.route|99"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"routeId":99,"date":"2024-01-15"}
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /all ─────────────────────────────────────────────────────────────
    @Test @DisplayName("POST /all → 201 with initialized message key")
    void createAll_returns201() throws Exception {
        mockMvc.perform(post(BASE + "/all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"date":"2024-01-15"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("routes.initialized.success"));
    }

    // ── GET /{date} ───────────────────────────────────────────────────────────
    @Nested @DisplayName("GET /{date}")
    class GetByDate {

        @Test @DisplayName("returns 200 with the operation list in data field")
        void returnsOperationList() throws Exception {
            RouteOperationDTO dto = new RouteOperationDTO(
                    1, LocalDate.of(2024, 1, 15),
                    new RouteOperationDTO.RouteRef(1, "1", true));
            when(routeOperationService.findAllByDate(LocalDate.of(2024, 1, 15))).thenReturn(List.of(dto));

            mockMvc.perform(get(BASE + "/2024-01-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test @DisplayName("returns 200 with empty array when no operations exist")
        void returnsEmptyArray() throws Exception {
            when(routeOperationService.findAllByDate(any())).thenReturn(List.of());

            mockMvc.perform(get(BASE + "/2024-01-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ── DELETE /{id} ──────────────────────────────────────────────────────────
    @Nested @DisplayName("DELETE /{id}")
    class DeleteById {

        @Test @DisplayName("200 with deleted message key when operation exists")
        void deletesSuccessfully() throws Exception {
            RouteOperation op = operation(1, route(1, "1"), LocalDate.of(2024, 1, 15));
            when(routeOperationService.findById(1)).thenReturn(op);

            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("operation.deleted.success"));

            verify(routeOperationService).softDelete(op);
        }

        @Test @DisplayName("404 when operation is not found")
        void returns404WhenNotFound() throws Exception {
            when(routeOperationService.findById(99))
                    .thenThrow(new NoSuchElementException("notFound.routeOperation|99"));

            mockMvc.perform(delete(BASE + "/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── DELETE /all/{date} ────────────────────────────────────────────────────
    @Test @DisplayName("DELETE /all/{date} → 200 with all-deleted message key")
    void deleteAll_returns200() throws Exception {
        mockMvc.perform(delete(BASE + "/all/2024-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("operations.all.deleted.success"));

        verify(routeOperationService).softDeleteAllByDate(LocalDate.of(2024, 1, 15));
    }

    // ── POST /vehicle/remove ──────────────────────────────────────────────────
    @Nested @DisplayName("POST /vehicle/remove")
    class RemoveVehicle {

        @Test @DisplayName("REMOVE_ONLY → 200 with vehicle.removed.success")
        void removeOnly_ok() throws Exception {
            postRemoveRequest("""
                    {"vehicleAssignmentId":1,"removalType":"REMOVE_ONLY"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("vehicle.removed.success"));
        }

        @Test @DisplayName("REMOVE_RECALCULATE with effectiveFrom → 200")
        void removeRecalculate_ok() throws Exception {
            postRemoveRequest("""
                    {"vehicleAssignmentId":1,"removalType":"REMOVE_RECALCULATE","effectiveFrom":"06:00:00"}
                    """)
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("REMOVE_REPLACE → 200")
        void removeReplace_ok() throws Exception {
            postRemoveRequest("""
                    {"vehicleAssignmentId":2,"removalType":"REMOVE_REPLACE"}
                    """)
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("missing vehicleAssignmentId → 400")
        void missingId_returns400() throws Exception {
            postRemoveRequest("""
                    {"removalType":"REMOVE_ONLY"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test @DisplayName("missing removalType → 400")
        void missingRemovalType_returns400() throws Exception {
            postRemoveRequest("""
                    {"vehicleAssignmentId":1}
                    """)
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("assignment not found → 404")
        void assignmentNotFound_returns404() throws Exception {
            doThrow(new NoSuchElementException("notFound.vehicleAssignment|99"))
                    .when(vehicleRemovalService).handleRemoval(any());

            postRemoveRequest("""
                    {"vehicleAssignmentId":99,"removalType":"REMOVE_ONLY"}
                    """)
                    .andExpect(status().isNotFound());
        }

        private ResultActions postRemoveRequest(String json) throws Exception {
            return mockMvc.perform(post(BASE + "/vehicle/remove")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json));
        }
    }
}
