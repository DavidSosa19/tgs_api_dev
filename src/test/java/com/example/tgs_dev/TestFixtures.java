package com.example.tgs_dev;

import com.example.tgs_dev.entity.*;
import com.example.tgs_dev.entity.enums.SchedulingMode;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import com.example.tgs_dev.service.schedule.TimeRangeLookup;
import com.example.tgs_dev.service.strategy.AssignmentSlot;

import java.time.LocalDate;
import java.time.LocalTime;

// suppress "unused" — factories are consumed across many test classes

/**
 * Shared factory for domain objects used across test classes.
 * Named parameters via builder-style static factories keep tests readable
 * without introducing a full builder framework.
 */
public final class TestFixtures {

    private TestFixtures() {}

    public static Company company(int id, String name) {
        Company c = new Company(name, "900000001-" + id);
        c.setId(id);
        // schedulingMode field-default is ROTATION_BASED — no setter needed
        return c;
    }

    public static Company company(int id, String name, SchedulingMode mode) {
        Company c = company(id, name);
        c.setSchedulingMode(mode);
        return c;
    }

    /**
     * Creates a lightweight {@link AssignmentSlot} that is NOT a
     * {@link RotationEntry} (useful for testing that the assignment pipeline
     * is decoupled from the concrete slot type).
     */
    public static AssignmentSlot slot(Vehicle v, ScheduleTemplate t) {
        return new AssignmentSlot() {
            @Override public Vehicle          getVehicle()          { return v; }
            @Override public ScheduleTemplate getScheduleTemplate() { return t; }
        };
    }

    public static Route route(int id, String number) {
        Route r = new Route(number);
        r.setId(id);
        return r;
    }

    /**
     * Creates a {@link TimeRangeLookup} value object for use in pure resolver tests.
     */
    public static TimeRangeLookup lookup(LocalTime start, LocalTime end, int durationMinutes) {
        return new TimeRangeLookup(start, end, durationMinutes, false);
    }

    /** Overnight lookup variant. */
    public static TimeRangeLookup overnightLookup(LocalTime start, LocalTime end, int durationMinutes) {
        return new TimeRangeLookup(start, end, durationMinutes, true);
    }

    public static Vehicle vehicle(int id, String number) {
        Vehicle v = new Vehicle(number, null);
        v.setId(id);
        return v;
    }

    public static ScheduleTemplate template(int id, Route route, LocalTime startTime) {
        ScheduleTemplate t = new ScheduleTemplate(route, "T" + id, "Template " + id, startTime);
        t.setId(id);
        return t;
    }

    public static RouteOperation operation(int id, Route route, LocalDate date) {
        RouteOperation ro = new RouteOperation(route, date);
        ro.setId(id);
        return ro;
    }

    public static VehicleAssignment assignment(int id, RouteOperation op, Vehicle v,
                                               ScheduleTemplate t, int rowOrder) {
        VehicleAssignment va = new VehicleAssignment(op, v, t, rowOrder);
        va.setId(id);
        va.setActive(true);
        return va;
    }

    public static Schedule schedule(int id, VehicleAssignment va, int order, LocalTime time) {
        Schedule s = new Schedule(va, order, time);
        s.setId(id);
        return s;
    }

    public static RotationEntry entry(Vehicle v, ScheduleTemplate t) {
        RotationEntry e = new RotationEntry();
        e.setVehicle(v);
        e.setScheduleTemplate(t);
        return e;
    }

    /**
     * Creates a {@link RouteOperationalPeriod} with the given parameters, linked
     * to a stub company. {@code effectiveTo} may be {@code null} for open-ended periods.
     */
    public static RouteOperationalPeriod operationalPeriod(int id, Route route,
                                                            int baseDuration, int cycleCount,
                                                            LocalDate effectiveFrom,
                                                            LocalDate effectiveTo) {
        Company c = company(1, "Corp");
        RouteOperationalPeriod p = new RouteOperationalPeriod(
                route, c, "Period " + id, baseDuration, cycleCount, effectiveFrom, effectiveTo);
        p.setId(id);
        return p;
    }

    /**
     * Creates a {@link ScheduleProjection} test double backed by an anonymous
     * class (Spring Data's proxy interface cannot be instantiated directly).
     *
     * <p>Alias names in {@code ScheduleRepository.findScheduleProjectionsByAssignmentIds}
     * must match these getter names; use this factory to keep test data consistent
     * with production query aliases.
     *
     * @param assignmentId   the owning assignment ID
     * @param departureOrder 1-based departure sequence number
     * @param time           the departure time
     * @return a lightweight test double implementing {@link ScheduleProjection}
     */
    public static ScheduleProjection scheduleProjection(int assignmentId,
                                                        int departureOrder,
                                                        LocalTime time) {
        return new ScheduleProjection() {
            @Override public Integer   getAssignmentId()   { return assignmentId; }
            @Override public Integer   getDepartureOrder() { return departureOrder; }
            @Override public LocalTime getDepartureTime()  { return time; }
        };
    }

    // ── Common dates ───────────────────────────────────────────────────────────
    /** Use OP_DATE (not DATE) to avoid collision with AssertJ's InstanceOfAssertFactories.DATE */
    public static final LocalDate OP_DATE = LocalDate.of(2024, 1, 15);
}
