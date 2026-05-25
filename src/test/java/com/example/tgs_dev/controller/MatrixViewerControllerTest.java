package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
import com.example.tgs_dev.controller.response.viewer.OperationHeaderDTO;
import com.example.tgs_dev.controller.response.viewer.OperationScheduleDTO;
import com.example.tgs_dev.controller.response.viewer.ScheduleEntryDTO;
import com.example.tgs_dev.controller.response.viewer.TemplateInfoDTO;
import com.example.tgs_dev.controller.response.viewer.VehicleInfoDTO;
import com.example.tgs_dev.controller.response.viewer.VehicleScheduleDTO;
import com.example.tgs_dev.service.MatrixService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatrixViewerController")
class MatrixViewerControllerTest {

    @Mock MatrixService             matrixService;
    @Mock ConstraintMessageResolver constraintResolver;

    MockMvc mockMvc;
    static final String BASE = "/api/viewer/route-operations";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MatrixViewerController(matrixService))
                .setControllerAdvice(new GlobalExceptionHandler(constraintResolver))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    // ── GET /schedules ─────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /route-operations/{id}/schedules")
    class GetOperationSchedules {

        private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);

        private OperationHeaderDTO header() {
            return new OperationHeaderDTO(1, SERVICE_DATE, 10, "R-10");
        }

        private VehicleScheduleDTO vehicleRow(int vaId, int rowOrder) {
            var vehicle  = new VehicleInfoDTO(vaId, "V-00" + vaId, "ABC-00" + vaId);
            var template = new TemplateInfoDTO(1, "T1", "Template 1", LocalTime.of(6, 0));
            var entries  = List.of(
                    new ScheduleEntryDTO(1, LocalTime.of(6,  0)),
                    new ScheduleEntryDTO(2, LocalTime.of(6, 30))
            );
            return new VehicleScheduleDTO(vaId, rowOrder, vehicle, template, entries);
        }

        @Test @DisplayName("200 with populated schedule")
        void ok_withSchedule() throws Exception {
            OperationScheduleDTO dto = new OperationScheduleDTO(
                    header(),
                    List.of(vehicleRow(1, 1), vehicleRow(2, 2))
            );
            when(matrixService.getOperationSchedules(1)).thenReturn(dto);

            mockMvc.perform(get(BASE + "/1/schedules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.operation.operationId").value(1))
                    .andExpect(jsonPath("$.data.operation.routeNumber").value("R-10"))
                    .andExpect(jsonPath("$.data.operation.serviceDate").value("2024-01-15"))
                    .andExpect(jsonPath("$.data.vehicleSchedules").isArray())
                    .andExpect(jsonPath("$.data.vehicleSchedules.length()").value(2))
                    .andExpect(jsonPath("$.data.vehicleSchedules[0].rowOrder").value(1))
                    .andExpect(jsonPath("$.data.vehicleSchedules[0].vehicle.vehicleNumber").value("V-001"))
                    .andExpect(jsonPath("$.data.vehicleSchedules[0].schedules[0].departureOrder").value(1))
                    .andExpect(jsonPath("$.data.vehicleSchedules[0].schedules[0].departureTime").value("06:00:00"));
        }

        @Test @DisplayName("200 with empty vehicleSchedules when operation has no assignments")
        void ok_emptySchedule() throws Exception {
            OperationScheduleDTO dto = OperationScheduleDTO.empty(header());
            when(matrixService.getOperationSchedules(1)).thenReturn(dto);

            mockMvc.perform(get(BASE + "/1/schedules"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.vehicleSchedules").isArray())
                    .andExpect(jsonPath("$.data.vehicleSchedules.length()").value(0));
        }

        @Test @DisplayName("404 when route operation not found for current tenant")
        void notFound() throws Exception {
            when(matrixService.getOperationSchedules(99))
                    .thenThrow(new NoSuchElementException("notFound.routeOperation|99"));

            mockMvc.perform(get(BASE + "/99/schedules"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /assignments (legacy) ──────────────────────────────────────────────

    @Nested @DisplayName("GET /route-operations/{id}/assignments (legacy)")
    class GetAssignments {

        @Test @DisplayName("200 with assignment list")
        void ok() throws Exception {
            when(matrixService.getAssignmentSchedules(1)).thenReturn(List.of());
            mockMvc.perform(get(BASE + "/1/assignments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test @DisplayName("404 when route operation not found")
        void notFound() throws Exception {
            when(matrixService.getAssignmentSchedules(99))
                    .thenThrow(new NoSuchElementException("notFound.routeOperation|99"));
            mockMvc.perform(get(BASE + "/99/assignments"))
                    .andExpect(status().isNotFound());
        }
    }
}
