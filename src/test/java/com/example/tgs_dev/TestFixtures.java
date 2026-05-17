package com.example.tgs_dev;

import com.example.tgs_dev.entity.*;

import java.time.LocalDate;
import java.time.LocalTime;

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
        return c;
    }

    public static Route route(int id, String number) {
        Route r = new Route(number, 30, 3);
        r.setId(id);
        return r;
    }

    public static Route route(int id, String number, int baseDuration, int cycleCount) {
        Route r = new Route(number, baseDuration, cycleCount);
        r.setId(id);
        return r;
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

    // ── Common dates ───────────────────────────────────────────────────────────
    /** Use OP_DATE (not DATE) to avoid collision with AssertJ's InstanceOfAssertFactories.DATE */
    public static final LocalDate OP_DATE = LocalDate.of(2024, 1, 15);
}
