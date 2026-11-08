package com.trafficflow.engine;

import com.trafficflow.config.SimulationConfig;
import com.trafficflow.controller.SignalController;
import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.SimulationSnapshot;
import com.trafficflow.model.Vehicle;
import com.trafficflow.model.VehicleSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core discrete-event simulation engine for the TrafficFlow system.
 *
 * Responsibilities:
 *  - Maintains the global simulation clock.
 *  - Advances simulation physics in deterministic, fixed time steps (dt).
 *  - Orchestrates vehicle spawning, traffic signal controllers, and intersection vehicle dynamics.
 *  - Provides lifecycle controls: start, pause, resume, reset, and step-by-step execution.
 *  - Produces immutable SimulationSnapshot instances for UI rendering and telemetry.
 *  - Completely independent of JavaFX or any graphical toolkit.
 */
public class SimulationEngine {

    public enum SimulationState {
        READY,
        RUNNING,
        PAUSED,
        COMPLETED
    }

    private final SimulationConfig config;
    private final Intersection intersection;
    private final VehicleSpawner spawner;
    private SignalController controller;

    private double currentSimTime = 0.0;
    private double accumulator = 0.0;
    private double speedMultiplier = 1.0;
    private SimulationState state = SimulationState.READY;

    /**
     * Constructs a SimulationEngine with specified configuration, intersection, and controller.
     *
     * @param config       simulation configuration and bounds
     * @param intersection the intersection model to manage
     * @param controller   active signal controller strategy
     */
    public SimulationEngine(SimulationConfig config, Intersection intersection, SignalController controller) {
        this.config = Objects.requireNonNull(config, "SimulationConfig cannot be null");
        this.intersection = Objects.requireNonNull(intersection, "Intersection cannot be null");
        this.controller = Objects.requireNonNull(controller, "SignalController cannot be null");
        this.spawner = new VehicleSpawner(config);
        this.currentSimTime = 0.0;
        this.accumulator = 0.0;
        this.speedMultiplier = 1.0;
        this.state = SimulationState.READY;
        this.intersection.setPhase(controller.getCurrentPhase());
    }

    /**
     * Convenience constructor creating a standard 4-way intersection.
     */
    public SimulationEngine(SimulationConfig config, SignalController controller) {
        this(config, new Intersection(), controller);
    }

    /**
     * Starts or resumes the simulation.
     */
    public void start() {
        if (state == SimulationState.COMPLETED) {
            return;
        }
        this.state = SimulationState.RUNNING;
    }

    /**
     * Pauses the simulation.
     */
    public void pause() {
        if (state == SimulationState.RUNNING) {
            this.state = SimulationState.PAUSED;
        }
    }

    /**
     * Resumes the simulation from a paused state.
     */
    public void resume() {
        start();
    }

    /**
     * Resets the entire simulation to time 0.0:
     * Clears all vehicles from all approaches, resets signal controller, and resets spawner seed.
     */
    public void reset() {
        this.currentSimTime = 0.0;
        this.accumulator = 0.0;
        this.state = SimulationState.READY;

        intersection.clear();
        controller.reset();
        spawner.reset();
        intersection.setPhase(controller.getCurrentPhase());
    }

    /**
     * Advances the simulation by exactly one fixed internal time step (config.getTimeStep()).
     * This is the fundamental, deterministic atomic tick of the simulation.
     */
    public void step() {
        if (state == SimulationState.COMPLETED) {
            return;
        }

        double dt = config.getTimeStep();

        // 1. Spawner generates new vehicles according to arrival distributions
        spawner.step(dt, currentSimTime, intersection);

        // 2. Controller updates signals and clearance state machines
        controller.update(dt, intersection);

        // 3. Intersection advances vehicle positions, enforces stop lines, queues, and clearance
        intersection.update(dt, currentSimTime);

        // 4. Advance simulation clock
        currentSimTime += dt;

        // 5. Check completion threshold
        if (currentSimTime >= config.getSimulationDuration() - 1e-6) {
            currentSimTime = config.getSimulationDuration();
            this.state = SimulationState.COMPLETED;
        }
    }

    /**
     * Advances the simulation based on elapsed real-world time.
     * Uses a time-accumulator model to execute an integer number of fixed physics steps (dt),
     * ensuring identical deterministic results regardless of rendering frame-rate or delta jitter.
     *
     * @param realDeltaSeconds elapsed wall-clock time in seconds
     */
    public void update(double realDeltaSeconds) {
        if (!Double.isFinite(realDeltaSeconds) || realDeltaSeconds <= 0.0) {
            return;
        }
        if (state != SimulationState.RUNNING) {
            return;
        }

        accumulator += realDeltaSeconds * speedMultiplier;
        double fixedDt = config.getTimeStep();

        // Prevent spiral of death on lag spikes (cap maximum steps per update)
        int maxStepsPerUpdate = 100;
        int stepsTaken = 0;

        while (accumulator >= fixedDt - 1e-9 && state == SimulationState.RUNNING && stepsTaken < maxStepsPerUpdate) {
            step();
            accumulator -= fixedDt;
            if (accumulator < 0.0) {
                accumulator = 0.0;
            }
            stepsTaken++;
        }

        if (stepsTaken >= maxStepsPerUpdate) {
            accumulator = 0.0; // Reset accumulator if overloaded
        }
    }

    /**
     * Creates an immutable, thread-safe snapshot of the current simulation state.
     *
     * @return SimulationSnapshot
     */
    public SimulationSnapshot createSnapshot() {
        Map<Direction, SignalColor> signals = new EnumMap<>(Direction.class);
        Map<Direction, Integer> queueLengths = new EnumMap<>(Direction.class);
        Map<Direction, Double> maxWaitTimes = new EnumMap<>(Direction.class);
        Map<Direction, List<VehicleSnapshot>> activeVehicles = new EnumMap<>(Direction.class);

        for (Direction dir : Direction.values()) {
            signals.put(dir, intersection.getSignal(dir));
            queueLengths.put(dir, intersection.getQueueLength(dir));
            maxWaitTimes.put(dir, intersection.getMaxWaitTime(dir));

            List<VehicleSnapshot> snapshots = new ArrayList<>();
            for (Vehicle v : intersection.getApproach(dir).getVehicles()) {
                snapshots.add(VehicleSnapshot.from(v));
            }
            activeVehicles.put(dir, snapshots);
        }

        return new SimulationSnapshot(
                currentSimTime,
                intersection.getCurrentPhase(),
                signals,
                queueLengths,
                maxWaitTimes,
                activeVehicles,
                spawner.getTotalVehiclesGenerated(),
                intersection.getTotalClearedVehicles(),
                intersection.getTotalActiveVehicles(),
                getAverageWaitTimeProcessed(),
                getMaxWaitTimeProcessed(),
                controller.getName(),
                state == SimulationState.COMPLETED,
                state == SimulationState.PAUSED
        );
    }

    /**
     * Calculates the average wait time of all vehicles that have cleared the intersection.
     */
    public double getAverageWaitTimeProcessed() {
        List<Vehicle> cleared = intersection.getAllClearedVehicles();
        if (cleared.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Vehicle v : cleared) {
            sum += v.getWaitTime();
        }
        return sum / cleared.size();
    }

    /**
     * Returns the maximum wait time experienced by any processed vehicle.
     */
    public double getMaxWaitTimeProcessed() {
        List<Vehicle> cleared = intersection.getAllClearedVehicles();
        double max = 0.0;
        for (Vehicle v : cleared) {
            if (v.getWaitTime() > max) {
                max = v.getWaitTime();
            }
        }
        return max;
    }

    public SimulationConfig getConfig() {
        return config;
    }

    public Intersection getIntersection() {
        return intersection;
    }

    public VehicleSpawner getSpawner() {
        return spawner;
    }

    public SignalController getController() {
        return controller;
    }

    /**
     * Replaces the active signal controller strategy.
     * Controller replacement is strictly permitted only when the simulation is in the READY state.
     *
     * @param newController new signal controller strategy
     * @throws NullPointerException if newController is null
     * @throws IllegalStateException if called while RUNNING, PAUSED, or COMPLETED
     */
    public void setController(SignalController newController) {
        Objects.requireNonNull(newController, "Controller cannot be null");
        if (state != SimulationState.READY) {
            throw new IllegalStateException("Controller replacement is permitted only in READY state, but current state is: " + state);
        }
        this.controller = newController;
        intersection.setPhase(controller.getCurrentPhase());
    }

    public double getCurrentSimTime() {
        return currentSimTime;
    }

    public SimulationState getState() {
        return state;
    }

    public boolean isRunning() {
        return state == SimulationState.RUNNING;
    }

    public boolean isPaused() {
        return state == SimulationState.PAUSED;
    }

    public boolean isCompleted() {
        return state == SimulationState.COMPLETED;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        if (!Double.isFinite(speedMultiplier) || speedMultiplier <= 0.0) {
            throw new IllegalArgumentException("Speed multiplier must be positive and finite: " + speedMultiplier);
        }
        this.speedMultiplier = speedMultiplier;
    }
}
