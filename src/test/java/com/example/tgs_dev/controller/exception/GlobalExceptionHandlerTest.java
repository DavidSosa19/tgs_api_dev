package com.example.tgs_dev.controller.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Mock ConstraintMessageResolver constraintResolver;
    GlobalExceptionHandler sut;

    @BeforeEach void setUp() { sut = new GlobalExceptionHandler(constraintResolver); }

    @Nested @DisplayName("handleAppException")
    class HandleAppException {
        @Test @DisplayName("uses the exception status and message")
        void mapsStatusAndMessage() {
            var ex = new BusinessException("some.key");
            var res = sut.handleAppException(ex);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(res.getBody().success()).isFalse();
            assertThat(res.getBody().message()).isEqualTo("some.key");
        }
    }

    @Nested @DisplayName("handleNotFound")
    class HandleNotFound {
        @Test @DisplayName("404 with the exception message")
        void returns404() {
            var res = sut.handleNotFound(new NoSuchElementException("notFound.thing|1"));
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(res.getBody().success()).isFalse();
        }
    }

    @Nested @DisplayName("handleDataIntegrity")
    class HandleDataIntegrity {
        @Test @DisplayName("known constraint → 409 with resolved message key")
        void knownConstraint() {
            var ex = new DataIntegrityViolationException("constraint [vehicle_vehicle_number_key]");
            when(constraintResolver.resolve(ex)).thenReturn("dup.vehicleNumber");
            var res = sut.handleDataIntegrity(ex);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(res.getBody().message()).isEqualTo("dup.vehicleNumber");
        }

        @Test @DisplayName("unknown constraint → 409 with integrity.generic")
        void unknownConstraint() {
            var ex = new DataIntegrityViolationException("some error");
            when(constraintResolver.resolve(ex)).thenReturn(null);
            var res = sut.handleDataIntegrity(ex);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(res.getBody().message()).isEqualTo("integrity.generic");
        }
    }

    @Nested @DisplayName("handleGeneric")
    class HandleGeneric {
        @Test @DisplayName("500 with error.unexpected")
        void returns500() {
            var res = sut.handleGeneric(new RuntimeException("boom"));
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(res.getBody().message()).isEqualTo("error.unexpected");
        }
    }
}
