package com.example.tgs_dev.controller;

import com.example.tgs_dev.controller.exception.ConstraintMessageResolver;
import com.example.tgs_dev.controller.exception.GlobalExceptionHandler;
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

    @Nested @DisplayName("GET /route-operations/{id}/assignments")
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
