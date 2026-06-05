package com.example.tgs_dev.service.schedule;

import com.example.tgs_dev.controller.exception.BusinessException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the ordered sequence of departure slots for a route on a single
 * service day, driven entirely by the headway configuration of the active
 * {@link RouteOperationalPeriod}.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Start at {@code period.firstDeparture}.</li>
 *   <li>Add the current time to the slot list.</li>
 *   <li>Resolve the headway for that time via the {@link HeadwayResolver} chain
 *       ({@link TimeRangeHeadwayResolver} → {@link FixedHeadwayResolver}).</li>
 *   <li>Advance the clock by the resolved headway.</li>
 *   <li>Repeat until the clock exceeds {@code period.lastDeparture}.</li>
 * </ol>
 *
 * <h3>Guarantees</h3>
 * <ul>
 *   <li>The returned list is <strong>never empty</strong> — at minimum it contains
 *       {@code firstDeparture}.</li>
 *   <li>All times are <strong>strictly increasing</strong> (headway > 0 is enforced
 *       by the resolver contract and validated post-resolution).</li>
 *   <li>No slot exceeds {@code lastDeparture}.</li>
 *   <li>The list is <strong>immutable</strong>.</li>
 * </ul>
 *
 * <h3>Separation of concerns</h3>
 * This class knows nothing about vehicles or assignments.  Its only responsibility
 * is: {@code (period, route, date) → List<LocalTime>}.  Vehicle-to-slot assignment
 * is done downstream in
 * {@link com.example.tgs_dev.service.ScheduleService#calculateVehicleSchedules}.
 */
@Component
public class DepartureSlotGenerator {

    /**
     * Safety cap: prevents infinite loops if a misconfigured headway of 0 somehow
     * bypasses the resolver contract check.  ~2 000 slots covers a 16-hour service
     * day at 30-second headway, far beyond any realistic transit scenario.
     */
    private static final int MAX_SLOTS = 2_000;

    private final HeadwayResolver headwayResolver;

    public DepartureSlotGenerator(HeadwayResolver headwayResolver) {
        this.headwayResolver = headwayResolver;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates the full departure slot sequence for {@code route} on {@code opDate}.
     *
     * @param period  the active operational period (must have non-null firstDeparture,
     *                lastDeparture, and defaultHeadwayMinutes).
     * @param route   the route being scheduled (passed to the resolver chain for
     *                seasonal-pattern and calendar-override lookups).
     * @param opDate  the service date.
     * @return immutable, non-empty list of departure times in ascending order.
     * @throws BusinessException       if {@code period} is missing departure-time
     *                                 or headway configuration.
     * @throws IllegalStateException   if the resolver chain violates its own contract
     *                                 (headway <= 0) or if the safety slot cap is hit.
     */
    public List<LocalTime> generate(RouteOperationalPeriod period,
                                    Route route,
                                    LocalDate opDate) {

        validatePeriodConfig(period);

        List<TimeRangeLookup> rangeLookups = buildRangeLookups(period);

        List<LocalTime> slots   = new ArrayList<>();
        LocalTime       current = period.getFirstDeparture();
        LocalTime       last    = period.getLastDeparture();

        while (!current.isAfter(last)) {

            if (slots.size() >= MAX_SLOTS) {
                throw new IllegalStateException(
                        "DepartureSlotGenerator exceeded the safety cap of " + MAX_SLOTS +
                        " slots for period id=" + period.getId() +
                        " on route " + route.getRouteNumber() +
                        ". Verify firstDeparture, lastDeparture and headway configuration.");
            }

            slots.add(current);

            ScheduleResolverContext ctx = new ScheduleResolverContext(
                    route,
                    current,
                    opDate,
                    period.getBaseDuration(),
                    period.getDefaultHeadwayMinutes(),
                    rangeLookups);

            int headway = headwayResolver.resolve(ctx);

            if (headway <= 0) {
                throw new IllegalStateException(
                        "HeadwayResolver returned a non-positive value (" + headway +
                        ") for slot " + current + " on route " + route.getRouteNumber() +
                        ". This violates the resolver contract — check chain configuration.");
            }

            current = current.plusMinutes(headway);
        }

        return List.copyOf(slots);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validatePeriodConfig(RouteOperationalPeriod period) {
        if (period.getFirstDeparture() == null || period.getLastDeparture() == null) {
            throw new BusinessException(
                    "validation.period.missingDepartureTimes|" + period.getId());
        }
        if (period.getDefaultHeadwayMinutes() == null || period.getDefaultHeadwayMinutes() <= 0) {
            throw new BusinessException(
                    "validation.period.missingDefaultHeadway|" + period.getId());
        }
        if (!period.getFirstDeparture().isBefore(period.getLastDeparture())) {
            throw new BusinessException(
                    "validation.period.invalidDepartureRange|" + period.getId());
        }
    }

    private List<TimeRangeLookup> buildRangeLookups(RouteOperationalPeriod period) {
        if (!period.isUseTimeRanges() || period.getTimeRanges().isEmpty()) {
            return List.of();
        }
        return period.getTimeRanges().stream()
                .map(r -> new TimeRangeLookup(
                        r.getRangeStart(),
                        r.getRangeEnd(),
                        r.getDurationMinutes(),
                        r.getHeadwayMinutes(),
                        r.isCrossesMidnight()))
                .toList();
    }
}
