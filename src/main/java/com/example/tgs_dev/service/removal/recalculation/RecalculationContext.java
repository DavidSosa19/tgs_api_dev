package com.example.tgs_dev.service.removal.recalculation;

import com.example.tgs_dev.entity.Schedule;
import com.example.tgs_dev.entity.VehicleAssignment;

import java.util.List;
import java.util.Map;

/**
 * Pre-processed data bundle handed to a {@link RecalculationAlgorithm}.
 *
 * <p>All schedule collections are already filtered (only departure times
 * &ge; the operator-supplied {@code fromTime} are included) and sorted
 * ascending by departure time.  Algorithms receive clean, ready-to-use data
 * and focus purely on the redistribution math.
 *
 * <h3>Invariants guaranteed by the producer</h3>
 * <ul>
 *   <li>{@link #removedSchedules} is non-empty — the strategy short-circuits
 *       before building this context when no qualifying schedules exist.</li>
 *   <li>{@link #candidates} is non-empty and sorted by {@code rowOrder} asc.</li>
 *   <li>Each list in {@link #qualifyingSchedules} is sorted asc by departure time.</li>
 *   <li>{@link #windowSize} equals {@code candidates.size()} for
 *       {@link com.example.tgs_dev.entity.enums.RecalculationScope#ALL_VEHICLES}.</li>
 * </ul>
 *
 * @param removedSchedules    qualifying schedules of the removed vehicle, sorted asc
 * @param candidates          remaining assignments sorted by {@code rowOrder} asc
 * @param qualifyingSchedules qualifying schedules per candidate assignment ID, each list sorted asc
 * @param windowSize          number of candidates to affect
 */
public record RecalculationContext(
        List<Schedule>               removedSchedules,
        List<VehicleAssignment>      candidates,
        Map<Integer, List<Schedule>> qualifyingSchedules,
        int                          windowSize
) {}
