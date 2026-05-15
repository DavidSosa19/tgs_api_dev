package com.example.tgs_dev.controller.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConstraintMessageResolver")
class ConstraintMessageResolverTest {

    private final ConstraintMessageResolver sut = new ConstraintMessageResolver();

    // ── Known constraints via Hibernate bracket format ─────────────────────────
    @Nested @DisplayName("Known unique constraints – bracket format [name]")
    class KnownUniqueBracket {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "schedule_template_template_number_key, dup.templateNumber",
            "vehicle_vehicle_number_key,            dup.vehicleNumber",
            "route_route_number_key,                dup.routeNumber",
            "person_document_number_key,            dup.documentNumber",
            "user_user_name_key,                    dup.userName"
        })
        void resolves(String constraint, String expectedKey) {
            DataIntegrityViolationException ex = bracketException(constraint);
            assertThat(sut.resolve(ex)).isEqualTo(expectedKey.trim());
        }
    }

    // ── Known FK constraints via Hibernate bracket format ─────────────────────
    @Nested @DisplayName("Known FK constraints – bracket format [name]")
    class KnownFkBracket {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
            "schedule_template_route_id_fkey,             fk.routeInTemplate",
            "schedule_template_secondary_route_id_fkey,   fk.routeSecondaryInTemplate",
            "vehicle_owner_id_fkey,                       fk.personIsVehicleOwner",
            "rotation_entry_vehicle_id_fkey,              fk.vehicleInRotation",
            "rotation_entry_template_id_fkey,             fk.templateInRotation",
            "rotation_entry_rotation_id_fkey,             fk.rotationHasEntries",
            "route_operation_route_id_fkey,               fk.routeInOperation",
            "vehicle_assignment_vehicle_id_fkey,          fk.vehicleInOperation",
            "vehicle_assignment_template_id_fkey,         fk.templateInOperation"
        })
        void resolves(String constraint, String expectedKey) {
            DataIntegrityViolationException ex = bracketException(constraint);
            assertThat(sut.resolve(ex)).isEqualTo(expectedKey.trim());
        }
    }

    // ── Known constraints via PSQL quote format "name" ────────────────────────
    @Nested @DisplayName("Known constraints – PSQL quote format \"name\"")
    class KnownQuoteFormat {

        @Test @DisplayName("unique constraint via root cause quote format")
        void uniqueViaRootCause() {
            DataIntegrityViolationException ex = quoteException("vehicle_vehicle_number_key");
            assertThat(sut.resolve(ex)).isEqualTo("dup.vehicleNumber");
        }

        @Test @DisplayName("FK constraint via root cause quote format")
        void fkViaRootCause() {
            DataIntegrityViolationException ex = quoteException("route_operation_route_id_fkey");
            assertThat(sut.resolve(ex)).isEqualTo("fk.routeInOperation");
        }
    }

    // ── Generic fallbacks ─────────────────────────────────────────────────────
    @Nested @DisplayName("Generic fallbacks when constraint name is unknown")
    class GenericFallbacks {

        @Test @DisplayName("unknown constraint with 'duplicate key' → dup.generic")
        void duplicateKey_returnsGenericDup() {
            DataIntegrityViolationException ex = plainMessageException(
                    "ERROR: duplicate key value violates unique constraint \"some_unknown_key\"");
            assertThat(sut.resolve(ex)).isEqualTo("dup.generic");
        }

        @Test @DisplayName("unknown constraint with 'still referenced' → fk.delete.generic")
        void stillReferenced_returnsGenericFkDelete() {
            DataIntegrityViolationException ex = plainMessageException(
                    "ERROR: update or delete on table violates foreign key constraint — still referenced");
            assertThat(sut.resolve(ex)).isEqualTo("fk.delete.generic");
        }

        @Test @DisplayName("unknown constraint with 'not present' → fk.insert.generic")
        void notPresent_returnsGenericFkInsert() {
            DataIntegrityViolationException ex = plainMessageException(
                    "ERROR: insert or update violates foreign key constraint — not present in table");
            assertThat(sut.resolve(ex)).isEqualTo("fk.insert.generic");
        }

        @Test @DisplayName("completely unknown violation → returns null")
        void completelyUnknown_returnsNull() {
            DataIntegrityViolationException ex = plainMessageException("Something weird happened");
            assertThat(sut.resolve(ex)).isNull();
        }

        @Test @DisplayName("null message → returns null")
        void nullMessage_returnsNull() {
            DataIntegrityViolationException ex = new DataIntegrityViolationException(null);
            assertThat(sut.resolve(ex)).isNull();
        }
    }

    // ── Cause chain traversal ─────────────────────────────────────────────────
    @Nested @DisplayName("Cause chain traversal")
    class CauseChain {

        @Test @DisplayName("resolves constraint name buried in cause chain")
        void buriedInCause() {
            RuntimeException root = new RuntimeException(
                    "constraint \"person_document_number_key\" violated");
            RuntimeException middle = new RuntimeException("wrapped", root);
            DataIntegrityViolationException ex =
                    new DataIntegrityViolationException("outer", middle);

            assertThat(sut.resolve(ex)).isEqualTo("dup.documentNumber");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Exception whose message uses Hibernate bracket format: constraint [name] */
    private static DataIntegrityViolationException bracketException(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement; SQL [n/a]; constraint [" + constraintName.trim() + "]");
    }

    /** Exception with PSQL quote format in the root cause: constraint "name" */
    private static DataIntegrityViolationException quoteException(String constraintName) {
        RuntimeException cause = new RuntimeException(
                "ERROR: duplicate key violates constraint \"" + constraintName.trim() + "\"");
        return new DataIntegrityViolationException("constraint violation", cause);
    }

    /** Exception with a plain, unstructured message (no recognisable constraint name). */
    private static DataIntegrityViolationException plainMessageException(String message) {
        return new DataIntegrityViolationException(message);
    }
}
