package com.trafficflow.model;

import java.util.Objects;

/**
 * Immutable snapshot record of an individual vehicle's state at an instant in time.
 * Designed for decoupled, safe UI rendering and telemetry without touching active domain models.
 */
public record VehicleSnapshot(
        int id,
        Direction origin,
        double position,
        double speed,
        double length,
        double waitTime,
        boolean isStopped,
        boolean passedStopLine
) {
    public VehicleSnapshot {
        Objects.requireNonNull(origin, "Origin direction cannot be null");
    }

    /**
     * Factory method creating an immutable snapshot from a domain Vehicle entity.
     *
     * @param vehicle active domain vehicle
     * @return immutable VehicleSnapshot
     */
    public static VehicleSnapshot from(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "Vehicle cannot be null");
        return new VehicleSnapshot(
                vehicle.getId(),
                vehicle.getOrigin(),
                vehicle.getPosition(),
                vehicle.getSpeed(),
                vehicle.getLength(),
                vehicle.getWaitTime(),
                vehicle.isStopped(),
                vehicle.hasPassedStopLine()
        );
    }
}
