package com.example.tgs_dev.service.schedule;

/**
 * Leaf node of the {@link HeadwayResolver} chain.
 *
 * <p>Always returns {@code context.effectiveDefaultHeadway()}, which is the
 * period-level fallback headway pre-resolved by
 * {@link DepartureSlotGenerator} before the chain is entered.
 *
 * <p>This resolver is instantiated directly inside
 * {@link com.example.tgs_dev.config.HeadwayResolverConfiguration} and is
 * <strong>not</strong> a Spring bean itself — it carries no state and is always
 * constructed as the terminal link of the chain.
 */
public final class FixedHeadwayResolver implements HeadwayResolver {

    /**
     * Returns {@code context.effectiveDefaultHeadway()} unconditionally.
     * Never delegates — always produces a result.
     */
    @Override
    public int resolve(ScheduleResolverContext context) {
        return context.effectiveDefaultHeadway();
    }
}
