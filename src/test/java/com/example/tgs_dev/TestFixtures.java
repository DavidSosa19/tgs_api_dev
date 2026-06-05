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

    /**
     * Creates a {@link Route} with a {@link RouteGroup} attached.
     * The group's id mirrors the route's surrogate id (cast to Long) so tests
     * that only care about identity don't need to think about both ids.
     */
    public static Route route(int id, String number) {
        Route r = new Route(number);
        r.setId(id);
        RouteGroup group = new RouteGroup(null, number);
        group.setId((long) id);
        r.setGroup(group);
        return r;
    }

    /**
     * Creates a {@link TimeRangeLookup} with duration data only (headwayMinutes = 0).
     * Suitable for duration-resolver tests where headway is not under test.
     */
    public static TimeRangeLookup lookup(LocalTime start, LocalTime end, int durationMinutes) {
        return TimeRangeLookup.durationOnly(start, end, durationMinutes, false);
    }

    /** Overnight lookup variant (duration only). */
    public static TimeRangeLookup overnightLookup(LocalTime start, LocalTime end, int durationMinutes) {
        return TimeRangeLookup.durationOnly(start, end, durationMinutes, true);
    }

    /**
     * Creates a {@link TimeRangeLookup} with both duration and headway data.
     */
    public static TimeRangeLookup lookup(LocalTime start, LocalTime end,
                                          int durationMinutes, int headwayMinutes) {
        return new TimeRangeLookup(start, end, durationMinutes, headwayMinutes, false);
    }

    public static Vehicle vehicle(int id, String number) {
        Vehicle v = new Vehicle(number, null);
        v.setId(id);
        return v;
    }

    public static ScheduleTemplate template(int id, Route route, int sequenceOrder) {
        ScheduleTemplate t = new ScheduleTemplate(route, "T" + id, "Template " + id, sequenceOrder);
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

    /**
     * Convenience: {@code tripNumber} defaults to {@code order} (matches the simple
     * case where the assignment has a single vehicle).  Use the 5-arg overload when
     * the test needs distinct global and per-vehicle indices.
     */
    public static Schedule schedule(int id, VehicleAssignment va, int order, LocalTime time) {
        return schedule(id, va, order, order, time);
    }

    public static Schedule schedule(int id, VehicleAssignment va,
                                     int departureOrder, int tripNumber, LocalTime time) {
        Schedule s = new Schedule(va, departureOrder, tripNumber, time);
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
     * Creates a {@link RouteOperationalPeriod} for tests.
     * {@code effectiveTo} may be {@code null} for open-ended periods.
     * Departure window defaults to 06:00 – 22:00.
     *
     * @param baseDuration          trip duration fallback (minutes)
     * @param defaultHeadwayMinutes departure slot spacing fallback (minutes)
     */
    public static RouteOperationalPeriod operationalPeriod(int id, Route route,
                                                            int baseDuration,
                                                            int defaultHeadwayMinutes,
                                                            LocalDate effectiveFrom,
                                                            LocalDate effectiveTo) {
        return operationalPeriod(id, route, baseDuration, defaultHeadwayMinutes,
                effectiveFrom, effectiveTo,
                LocalTime.of(6, 0), LocalTime.of(22, 0));
    }

    /**
     * Full-control overload — allows specifying custom departure times.
     */
    public static RouteOperationalPeriod operationalPeriod(int id, Route route,
                                                            int baseDuration,
                                                            int defaultHeadwayMinutes,
                                                            LocalDate effectiveFrom,
                                                            LocalDate effectiveTo,
                                                            LocalTime firstDeparture,
                                                            LocalTime lastDeparture) {
        Company c = company(1, "Corp");
        RouteOperationalPeriod p = new RouteOperationalPeriod(
                route, c, "Period " + id,
                baseDuration, defaultHeadwayMinutes,
                firstDeparture, lastDeparture,
                effectiveFrom, effectiveTo);
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
        return scheduleProjection(assignmentId, departureOrder, departureOrder, time);
    }

    public static ScheduleProjection scheduleProjection(int assignmentId,
                                                        int departureOrder,
                                                        int tripNumber,
                                                        LocalTime time) {
        return new ScheduleProjection() {
            @Override public Integer   getScheduleId()            { return assignmentId * 1000 + departureOrder; }
            @Override public Integer   getAssignmentId()          { return assignmentId; }
            @Override public Integer   getTripNumber()            { return tripNumber; }
            @Override public Integer   getDepartureOrder()        { return departureOrder; }
            @Override public LocalTime getDepartureTime()         { return time; }
            @Override public Boolean   getActive()                { return Boolean.TRUE; }
            @Override public String    getOrigin()                { return "ORIGINAL"; }
            @Override public LocalTime getOriginalDepartureTime() { return null; }
            @Override public String    getSupersededReason()      { return null; }
        };
    }

    // ── Common dates ───────────────────────────────────────────────────────────
    /** Use OP_DATE (not DATE) to avoid collision with AssertJ's InstanceOfAssertFactories.DATE */
    public static final LocalDate OP_DATE = LocalDate.of(2024, 1, 15);
}
