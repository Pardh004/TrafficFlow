package com.trafficflow.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot record of the complete intersection simulation state at a discrete point in time.
 * Consumed by JavaFX UI components (Canvas, KPI cards, charts) to ensure thread-safe, decoupled presentation.
 */
public record SimulationSnapshot(
        double simulationTime,
        TrafficPhase currentPhase,
        Map<Direction, SignalColor> signals,
        Map<Direction, Integer> queueLengths,
        Map<Direction, Double> maxWaitTimes,
        Map<Direction, List<VehicleSnapshot>> activeVehicles,
        int totalVehiclesGenerated,
        int totalVehiclesProcessed,
        int totalVehiclesActive,
        double averageWaitTimeProcessed,
        double maxWaitTimeProcessed,
        String controllerName,
        boolean isCompleted,
        boolean isPaused
) {
    public SimulationSnapshot {
        Objects.requireNonNull(currentPhase, "Current phase cannot be null");
        Objects.requireNonNull(controllerName, "Controller name cannot be null");

        signals = Collections.unmodifiableMap(new EnumMap<>(signals));
        queueLengths = Collections.unmodifiableMap(new EnumMap<>(queueLengths));
        maxWaitTimes = Collections.unmodifiableMap(new EnumMap<>(maxWaitTimes));

        Map<Direction, List<VehicleSnapshot>> vehiclesCopy = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            List<VehicleSnapshot> list = (activeVehicles != null) ? activeVehicles.get(dir) : null;
            vehiclesCopy.put(dir, (list != null) ? List.copyOf(list) : List.of());
        }
        activeVehicles = Collections.unmodifiableMap(vehiclesCopy);
    }
}
