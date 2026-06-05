package com.example.tgs_dev.service.removal;

import com.example.tgs_dev.entity.enums.RemovalType;

/**
 * Strategy for handling vehicle removal from a route operation.
 *
 * <p>Each implementation is a Spring {@code @Component} and self-identifies
 * via {@link #supports()}.  {@link com.example.tgs_dev.service.VehicleRemovalService}
 * collects all implementations at startup into a dispatch map and routes each
 * request to the matching strategy.
 *
 * <h3>Implementing a new strategy</h3>
 * <pre>{@code
 * @Component
 * public class CustomRemovalStrategy implements VehicleRemovalStrategy {
 *
 *     @Override
 *     public RemovalType supports() { return RemovalType.CUSTOM_MODE; }
 *
 *     @Override
 *     public RemovalOutcome execute(RemovalContext ctx) {
 *         // validate ctx fields, apply business logic, return outcome
 *         return RemovalOutcome.empty();
 *     }
 * }
 * }</pre>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Implementations validate their own required {@code ctx} fields and throw
 *       {@link com.example.tgs_dev.controller.exception.BusinessException} for
 *       missing or invalid parameters.</li>
 *   <li>Runs within the transaction opened by the orchestrator service — do
 *       not start a nested transaction unless explicitly required.</li>
 *   <li>{@link #supports()} must return a unique value across all registered beans.</li>
 *   <li>Returns a {@link RemovalOutcome} (never {@code null}) the orchestrator
 *       uses to emit the post-removal event.</li>
 * </ul>
 */
public interface VehicleRemovalStrategy {

    /**
     * The {@link RemovalType} this strategy handles.
     * Must be unique across all registered strategy beans.
     */
    RemovalType supports();

    /**
     * Executes the removal logic for the given context.
     *
     * @param ctx removal parameters and the already-resolved, still-active assignment
     * @return strategy-specific outputs; never {@code null}
     */
    RemovalOutcome execute(RemovalContext ctx);
}
