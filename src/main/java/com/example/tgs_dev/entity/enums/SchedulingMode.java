package com.example.tgs_dev.entity.enums;

/**
 * Discriminates which daily-schedule initialisation strategy a company uses.
 *
 * <p>Each constant maps 1-to-1 with a
 * {@link com.example.tgs_dev.service.strategy.ScheduleInitStrategy} bean.
 *
 * <h3>Extending with a new strategy</h3>
 * <ol>
 *   <li>Add a constant here.</li>
 *   <li>Implement {@code ScheduleInitStrategy}, return the constant from
 *       {@code mode()}, and annotate the class with {@code @Component}.</li>
 *   <li>Add the new value to the {@code CHECK} constraint in the next
 *       migration script ({@code V8__...sql}) so existing companies are not
 *       affected.</li>
 *   <li>Update the {@code AdminCompanyService} / frontend if operators need to
 *       switch modes per company.</li>
 * </ol>
 */
public enum SchedulingMode {

    /**
     * Default: vehicles rotate daily following a pre-defined cartulina
     * (VehicleRotation) schedule.
     */
    ROTATION_BASED
}
