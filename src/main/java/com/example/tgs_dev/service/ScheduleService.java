package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.entity.enums.ScheduleOrigin;
import com.example.tgs_dev.repository.ScheduleRepository;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import com.example.tgs_dev.service.schedule.DepartureSlotGenerator;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduleService {

    private final ScheduleRepository            scheduleRepository;
    private final TenantService                 tenantService;
    private final DepartureSlotGenerator        slotGenerator;
    private final RouteOperationalPeriodService periodService;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           TenantService tenantService,
                           DepartureSlotGenerator slotGenerator,
                           RouteOperationalPeriodService periodService) {
        this.scheduleRepository = scheduleRepository;
        this.tenantService      = tenantService;
        this.slotGenerator      = slotGenerator;
        this.periodService      = periodService;
    }

    public List<Schedule> findAll(){ return scheduleRepository.findAll(); }

    public Schedule save(Schedule schedule){
        return scheduleRepository.save(schedule);
    }

    public Optional<Schedule> findById(Integer id){
        return scheduleRepository.findById(id);
    }

    public void delete(Schedule schedule){
        scheduleRepository.delete(schedule);
    }

    public List<Schedule> findAllByAssignment(List<Integer> ids) {
        Specification<Schedule> specification = (root, query, cb) ->
                root.join("vehicleAssignment").get("id").in(ids);
        return scheduleRepository.findAll(specification);
    }

    /**
     * Fetches lean {@link ScheduleProjection} rows for the given assignment IDs
     * in a single batch query.
     *
     * <p>Projections carry only {@code assignmentId}, {@code departureOrder}, and
     * {@code departureTime} — no {@code company} EAGER join, no audit fields.
     */
    public List<ScheduleProjection> findScheduleProjections(List<Integer> assignmentIds) {
        if (assignmentIds.isEmpty()) return List.of();
        return scheduleRepository.findScheduleProjectionsByAssignmentIds(assignmentIds);
    }

    /**
     * Fetches projections for {@link com.example.tgs_dev.entity.enums.ScheduleOrigin#ORIGINAL}
     * schedules of the given assignments — regardless of whether each row is
     * currently active.  Used by the audit / original-plan endpoint.
     */
    public List<ScheduleProjection> findOriginalProjections(List<Integer> assignmentIds) {
        if (assignmentIds.isEmpty()) return List.of();
        return scheduleRepository.findOriginalProjectionsByAssignmentIds(assignmentIds);
    }

    public List<Schedule> findAllById(List<Integer> ids){
        return scheduleRepository.findAllById(ids);
    }

    public List<Schedule> saveAll(List<Schedule> schedules) {
        return scheduleRepository.saveAll(schedules);
    }

    /**
     * Persists and immediately flushes — used when the caller needs the SQL to
     * hit the database before subsequent inserts in the same transaction.
     *
     * <p>Hibernate's default action ordering executes INSERTs before UPDATEs
     * within a single flush.  When a recalculation supersedes a row
     * (sets {@code active=false}) and immediately creates a new row with the
     * same {@code (vehicle_assignment_id, departure_order)}, the unflushed
     * UPDATE leaves the old row visible to the partial unique index, causing
     * the new INSERT to fail with a duplicate-key error.  Flushing the
     * supersedes first applies the UPDATEs before the INSERTs run.
     */
    public List<Schedule> saveAllAndFlush(List<Schedule> schedules) {
        return scheduleRepository.saveAllAndFlush(schedules);
    }

    /**
     * Generates and persists all departure schedules for the given vehicle assignments.
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li>Resolves the active {@link RouteOperationalPeriod} for the operation date.
     *       Throws {@link com.example.tgs_dev.controller.exception.BusinessException}
     *       if none is configured.</li>
     *   <li>Delegates slot generation to {@link DepartureSlotGenerator}, which produces
     *       a monotonically increasing sequence of {@code LocalTime} values driven by the
     *       period's headway configuration.  The sequence runs from
     *       {@code period.firstDeparture} to {@code period.lastDeparture}.</li>
     *   <li>Sorts assignments by {@code scheduleTemplate.sequenceOrder} (ascending) to
     *       establish the fixed vehicle dispatch order required by the company.</li>
     *   <li>Distributes slots to vehicles in round-robin order: vehicle at position
     *       {@code p} (0-indexed) receives slots {@code p}, {@code p+N}, {@code p+2N}…
     *       where {@code N} is the number of vehicles.  This guarantees that the same
     *       vehicle always follows the same predecessor in every cycle.</li>
     *   <li>Persists all {@link Schedule} entries in a single batch.</li>
     * </ol>
     *
     * <h3>Two independent ordering dimensions</h3>
     * Each generated {@link Schedule} carries:
     * <ul>
     *   <li>{@code departureOrder} — global slot index in the day (1..N total slots).
     *       Reflects the chronological sequence of the entire route's service.</li>
     *   <li>{@code tripNumber}    — per-vehicle trip index (1..K, where K is
     *       {@code ceil(totalSlots / vehicleCount)}).  Reflects "this is the n-th
     *       round of this specific bus".  Derived from the round-robin assignment.</li>
     * </ul>
     *
     * <h3>Ordering guarantee</h3>
     * Because slots are always strictly increasing (enforced by
     * {@link DepartureSlotGenerator}) and round-robin preserves relative order,
     * each vehicle's own departure times are also strictly increasing.  Hence
     * sorting a vehicle's schedules by {@code tripNumber} or by {@code departureTime}
     * yields the same result.
     *
     * @param assignments  all vehicle assignments for a single route operation;
     *                     must share the same route and service date.
     */
    public void calculateVehicleSchedules(List<VehicleAssignment> assignments) {
        if (assignments.isEmpty()) return;

        Company company   = tenantService.currentCompany();
        Integer companyId = company.getId();

        // All assignments belong to the same operation — derive route and date once.
        VehicleAssignment first = assignments.get(0);
        Route             route  = first.getScheduleTemplate().getRoute();
        LocalDate         opDate = first.getRouteOperation().getServiceDate();

        // ── 1. Resolve active period (throws BusinessException if missing) ────
        RouteOperationalPeriod period =
                periodService.findActiveForDateOrThrow(route, companyId, opDate);

        // ── 2. Generate all departure slots for the day ───────────────────────
        List<LocalTime> slots = slotGenerator.generate(period, route, opDate);

        // ── 3. Sort vehicles by their fixed dispatch order ────────────────────
        List<VehicleAssignment> ordered = assignments.stream()
                .sorted(Comparator.comparingInt(
                        a -> a.getScheduleTemplate().getSequenceOrder()))
                .toList();

        int vehicleCount = ordered.size();

        // ── 4. Assign slots to vehicles in round-robin ────────────────────────
        // departureOrder = slotIndex + 1     (global, 1-based)
        // tripNumber     = slotIndex / N + 1 (per-vehicle, 1-based)
        List<Schedule> schedules = new ArrayList<>(slots.size());

        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            VehicleAssignment assignment   = ordered.get(slotIndex % vehicleCount);
            int               tripNumber   = slotIndex / vehicleCount + 1;
            Schedule          entry        = new Schedule(
                    assignment,
                    slotIndex + 1,
                    tripNumber,
                    slots.get(slotIndex));
            entry.setCompany(company);
            schedules.add(entry);
        }

        // ── 5. Persist in a single batch ──────────────────────────────────────
        scheduleRepository.saveAll(schedules);
    }
}
