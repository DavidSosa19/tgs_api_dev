package com.example.tgs_dev.service.schedule;

/**
 * Leaf node of the {@link DurationResolver} chain.
 *
 * <p>Always returns {@code route.baseDuration}.  This is the ultimate fallback —
 * it never delegates further and is guaranteed to produce a positive value as long
 * as route data is valid (baseDuration > 0 is enforced by validation on Route).
 *
 * <p>This resolver is instantiated directly inside
 * {@link com.example.tgs_dev.config.DurationResolverConfiguration} and is
 * <strong>not</strong> a Spring bean itself — it carries no state and is always
 * constructed as the terminal link of the chain.
 */
public final class FixedDurationResolver implements DurationResolver {

    /**
     * Returns {@code context.effectiveBaseDuration()} — the period-resolved base
     * duration pre-computed by {@link com.example.tgs_dev.service.ScheduleService}
     * from the active {@link com.example.tgs_dev.entity.RouteOperationalPeriod}
     * before the chain is invoked.
     */
    @Override
    public int resolve(DurationResolverContext context) {
        return context.effectiveBaseDuration();
    }
}
