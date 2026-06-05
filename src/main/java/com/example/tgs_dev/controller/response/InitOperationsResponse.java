package com.example.tgs_dev.controller.response;

import com.example.tgs_dev.service.InitOperationsResult;
import com.example.tgs_dev.service.InitOperationsResult.RouteInitFailure;

import java.util.List;

/**
 * Read model for the result of
 * {@code POST /api/routeOperation/all} (bulk operation initialisation).
 *
 * <p>Carries per-route outcomes so the frontend can present a precise summary
 * (e.g. "5 inicializadas, 1 omitida (ya existía), 1 falló: ruta 7 sin
 * configuración de horario").
 */
public record InitOperationsResponse(
        int                        initialized,
        int                        skipped,
        int                        failed,
        List<RouteInitFailureDTO>  failures
) {

    public static InitOperationsResponse from(InitOperationsResult result) {
        List<RouteInitFailureDTO> failures = result.failures().stream()
                .map(RouteInitFailureDTO::from)
                .toList();
        return new InitOperationsResponse(
                result.initialized(),
                result.skipped(),
                result.failures().size(),
                failures);
    }

    /**
     * Per-route failure detail.  {@code reason} is the i18n message key (with
     * pipe-separated parameters when applicable) that the frontend resolves to
     * a localised string.
     */
    public record RouteInitFailureDTO(
            Long   routeGroupId,
            String routeNumber,
            String reason
    ) {
        public static RouteInitFailureDTO from(RouteInitFailure f) {
            return new RouteInitFailureDTO(f.routeGroupId(), f.routeNumber(), f.reason());
        }
    }
}
