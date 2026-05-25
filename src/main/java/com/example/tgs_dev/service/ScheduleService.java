package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Company;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperationalPeriod;
import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.ScheduleTemplate;
import com.example.tgs_dev.entity.ScheduleTemplateVersion;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.ScheduleRepository;
import com.example.tgs_dev.repository.projection.ScheduleProjection;
import com.example.tgs_dev.service.schedule.DurationResolver;
import com.example.tgs_dev.service.schedule.DurationResolverContext;
import com.example.tgs_dev.service.schedule.TimeRangeLookup;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduleService {

    private final ScheduleRepository              scheduleRepository;
    private final TenantService                   tenantService;
    private final DurationResolver                durationResolver;
    private final RouteOperationalPeriodService   periodService;
    private final ScheduleTemplateVersionService  templateVersionService;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           TenantService tenantService,
                           DurationResolver durationResolver,
                           RouteOperationalPeriodService periodService,
                           ScheduleTemplateVersionService templateVersionService) {
        this.scheduleRepository    = scheduleRepository;
        this.tenantService         = tenantService;
        this.durationResolver      = durationResolver;
        this.periodService         = periodService;
        this.templateVersionService = templateVersionService;
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
     * This makes the result-set significantly smaller than loading full
     * {@link Schedule} entities when an operation has many vehicles × departures.
     *
     * <p>Returns an empty list when {@code assignmentIds} is empty so callers do
     * not need to guard against the call themselves (the guard is here to avoid
     * issuing an {@code IN ()} with an empty list, which some JDBC drivers reject).
     *
     * @param assignmentIds IDs of the vehicle assignments to fetch schedules for
     * @return lean projection rows ordered by assignment ID then departure order
     */
    public List<ScheduleProjection> findScheduleProjections(List<Integer> assignmentIds) {
        if (assignmentIds.isEmpty()) return List.of();
        return scheduleRepository.findScheduleProjectionsByAssignmentIds(assignmentIds);
    }

    public List<Schedule> findAllById(List<Integer> ids){
        return scheduleRepository.findAllById(ids);
    }

    public List<Schedule> saveAll(List<Schedule> schedules) {
        return scheduleRepository.saveAll(schedules);
    }
    /**
     * Generates and persists all departure schedules for a list of vehicle assignments.
     *
     * <p>For each assignment the algorithm:
     * <ol>
     *   <li>Resolves the <em>mandatory</em> active {@link RouteOperationalPeriod} for
     *       the operation date via {@link RouteOperationalPeriodService#findActiveForDateOrThrow}.
     *       A {@link com.example.tgs_dev.controller.exception.BusinessException} is
     *       thrown immediately if no period is configured — callers must ensure periods
     *       are set up before generating schedules.</li>
     *   <li>Reads {@code cycleCount}, {@code baseDuration}, and (when
     *       {@code useTimeRanges = true}) the time-range lookups from that period.</li>
     *   <li>Resolves the active {@link ScheduleTemplateVersion} for the operation
     *       date (optional); falls back to the {@link ScheduleTemplate}'s own
     *       {@code startTime} when no version is active.</li>
     *   <li>Starts at the resolved {@code startTime}.</li>
     *   <li>Creates {@code cycleCount} schedule entries, one per departure.</li>
     *   <li>After each entry, advances the clock by the duration resolved for that
     *       departure time via the {@link DurationResolver} chain:
     *       seasonal → calendar override → time ranges → fixed baseDuration.</li>
     * </ol>
     *
     * <p>The operation date comes from {@code assignment.routeOperation.serviceDate}
     * so that all date-based resolvers evaluate consistently.
     *
     * <p><em>Performance note:</em> all assignments in a single call typically share
     * the same route and operation date (one {@link com.example.tgs_dev.entity.RouteOperation}
     * per call from {@link OperationOrchestratorService}).  The period lookup is
     * O(1) per unique (route, date) pair; caching across assignments is a future
     * optimisation if needed.
     */
    public void calculateVehicleSchedules(List<VehicleAssignment> assignments) {
        Company   company   = tenantService.currentCompany();
        Integer   companyId = company.getId();
        List<Schedule> schedules = new ArrayList<>();

        for (VehicleAssignment assignment : assignments) {
            ScheduleTemplate template = assignment.getScheduleTemplate();
            Route            route    = template.getRoute();
            LocalDate        opDate   = assignment.getRouteOperation().getServiceDate();

            // ── Resolve active period (mandatory — throws if missing) ─────────
            RouteOperationalPeriod period =
                    periodService.findActiveForDateOrThrow(route, companyId, opDate);

            int effectiveCycleCount   = period.getCycleCount();
            int effectiveBaseDuration = period.getBaseDuration();

            List<TimeRangeLookup> effectiveTimeRanges = period.isUseTimeRanges()
                    ? period.getTimeRanges().stream()
                            .map(r -> new TimeRangeLookup(r.getRangeStart(), r.getRangeEnd(),
                                                          r.getDurationMinutes(),
                                                          r.isCrossesMidnight()))
                            .toList()
                    : List.of();

            // ── Resolve effective start time (optional version override) ──────
            LocalTime startTime = templateVersionService
                    .findActiveForDate(template, companyId, opDate)
                    .map(ScheduleTemplateVersion::getStartTime)
                    .orElse(template.getStartTime());

            // ── Generate departure entries ────────────────────────────────────
            LocalTime time = startTime;
            for (int i = 0; i < effectiveCycleCount; i++) {
                Schedule entry = new Schedule(assignment, i + 1, time);
                entry.setCompany(company);
                schedules.add(entry);

                // Resolve the gap for THIS departure time, then advance the clock.
                int durationMinutes = durationResolver.resolve(
                        new DurationResolverContext(route, time, opDate,
                                                    effectiveBaseDuration, effectiveTimeRanges));
                time = time.plusMinutes(durationMinutes);
            }
        }

        scheduleRepository.saveAll(schedules);
    }
}
