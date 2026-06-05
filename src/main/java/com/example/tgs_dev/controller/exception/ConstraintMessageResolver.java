package com.example.tgs_dev.controller.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps PostgreSQL constraint names to i18n message keys.
 *
 * <p>The returned values are <strong>English keys</strong> (e.g. {@code "dup.vehicleNumber"})
 * that the frontend resolves to a localised string via its i18n service.
 * This keeps all human-readable text in the frontend and lets the API remain
 * language-agnostic.
 *
 * <p>Constraint names follow PostgreSQL's default naming conventions:
 * <ul>
 *   <li>Unique : {@code {table}_{column}_key}</li>
 *   <li>FK     : {@code {table}_{column}_fkey}</li>
 * </ul>
 *
 * Add a new entry here whenever a new constraint is introduced in the schema.
 */
@Component
public class ConstraintMessageResolver {

    // ── Regex patterns to extract the constraint name from exception messages ──

    /** Hibernate wraps the name as: constraint [constraint_name] */
    private static final Pattern CONSTRAINT_BRACKET =
            Pattern.compile("constraint \\[([^\\]]+)\\]");

    /** PostgreSQL JDBC driver reports: constraint "constraint_name" */
    private static final Pattern CONSTRAINT_QUOTE =
            Pattern.compile("constraint \"([^\"]+)\"");

    // ── Constraint → i18n key map ─────────────────────────────────────────────

    private static final Map<String, String> MESSAGES = Map.ofEntries(

        // ── Unique constraints ────────────────────────────────────────────────
        Map.entry("schedule_template_template_number_key",       "dup.templateNumber"),
        Map.entry("uq_schedule_template_route_current_order",    "dup.sequenceOrder"),
        Map.entry("vehicle_vehicle_number_key",            "dup.vehicleNumber"),
        Map.entry("route_route_number_key",                "dup.routeNumber"),
        Map.entry("person_document_number_key",            "dup.documentNumber"),
        Map.entry("user_user_name_key",                    "dup.userName"),

        // ── FK violations when trying to DELETE a still-referenced record ─────
        Map.entry("schedule_template_route_id_fkey",             "fk.routeInTemplate"),
        Map.entry("schedule_template_secondary_route_id_fkey",   "fk.routeSecondaryInTemplate"),
        Map.entry("vehicle_owner_id_fkey",                       "fk.personIsVehicleOwner"),
        Map.entry("rotation_entry_vehicle_id_fkey",              "fk.vehicleInRotation"),
        Map.entry("rotation_entry_template_id_fkey",             "fk.templateInRotation"),
        Map.entry("rotation_entry_rotation_id_fkey",             "fk.rotationHasEntries"),
        Map.entry("route_operation_route_id_fkey",               "fk.routeInOperation"),
        Map.entry("vehicle_assignment_vehicle_id_fkey",          "fk.vehicleInOperation"),
        Map.entry("vehicle_assignment_template_id_fkey",         "fk.templateInOperation")
    );

    // Generic fallback keys
    private static final String GENERIC_UNIQUE =    "dup.generic";
    private static final String GENERIC_FK_DELETE = "fk.delete.generic";
    private static final String GENERIC_FK_INSERT = "fk.insert.generic";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves a {@link DataIntegrityViolationException} to an i18n message key.
     * Returns {@code null} if the exception is not recognisable as a constraint violation
     * (caller should fall back to its own handling).
     */
    public String resolve(DataIntegrityViolationException ex) {
        String constraintName = extractConstraintName(ex);

        if (constraintName != null) {
            String key = MESSAGES.get(constraintName);
            if (key != null) return key;
        }

        // Classify by violation type even without a known constraint name
        String fullMessage = buildFullMessage(ex);
        if (fullMessage.contains("duplicate key"))                                    return GENERIC_UNIQUE;
        if (fullMessage.contains("foreign key") && fullMessage.contains("still referenced")) return GENERIC_FK_DELETE;
        if (fullMessage.contains("foreign key") && fullMessage.contains("not present"))      return GENERIC_FK_INSERT;

        return null; // Unknown — let the caller use its own fallback
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String extractConstraintName(DataIntegrityViolationException ex) {
        // 1) Try Hibernate's wrapper message: "constraint [name]"
        String msg  = safeMsg(ex);
        String name = matchFirst(CONSTRAINT_BRACKET, msg);
        if (name != null) return name;

        // 2) Try the PSQLException root cause: constraint "name"
        Throwable root = ex.getRootCause();
        if (root != null) {
            name = matchFirst(CONSTRAINT_QUOTE, safeMsg(root));
            if (name != null) return name;
        }

        // 3) Walk the full cause chain
        Throwable cause = ex.getCause();
        while (cause != null) {
            name = matchFirst(CONSTRAINT_QUOTE, safeMsg(cause));
            if (name != null) return name;
            name = matchFirst(CONSTRAINT_BRACKET, safeMsg(cause));
            if (name != null) return name;
            cause = cause.getCause();
        }

        return null;
    }

    private String buildFullMessage(DataIntegrityViolationException ex) {
        StringBuilder sb = new StringBuilder(safeMsg(ex));
        Throwable cause = ex.getCause();
        while (cause != null) {
            sb.append(' ').append(safeMsg(cause));
            cause = cause.getCause();
        }
        return sb.toString().toLowerCase();
    }

    private static String matchFirst(Pattern pattern, String input) {
        if (input == null || input.isBlank()) return null;
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    private static String safeMsg(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : "";
    }
}
