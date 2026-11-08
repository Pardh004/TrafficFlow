package com.trafficflow.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate root model representing the complete 4-way intersection.
 * Holds the four approach lanes (NORTH, SOUTH, EAST, WEST) and the current traffic signal phase.
 */
public class Intersection {

    private final Map<Direction, ApproachLane> approaches = new EnumMap<>(Direction.class);
    private TrafficPhase currentPhase;

    /**
     * Constructs a 4-way intersection with standard default approach lane geometry.
     */
    public Intersection() {
        for (Direction dir : Direction.values()) {
            approaches.put(dir, new ApproachLane(dir));
        }
        // Initialize in safe all-red clearance phase
        this.currentPhase = TrafficPhase.ALL_RED;
    }

    /**
     * Constructs an intersection with explicitly provided approach lanes.
     *
     * @param north North approach
     * @param south South approach
     * @param east  East approach
     * @param west  West approach
     */
    public Intersection(ApproachLane north, ApproachLane south, ApproachLane east, ApproachLane west) {
        approaches.put(Direction.NORTH, Objects.requireNonNull(north, "North lane cannot be null"));
        approaches.put(Direction.SOUTH, Objects.requireNonNull(south, "South lane cannot be null"));
        approaches.put(Direction.EAST, Objects.requireNonNull(east, "East lane cannot be null"));
        approaches.put(Direction.WEST, Objects.requireNonNull(west, "West lane cannot be null"));
        this.currentPhase = TrafficPhase.ALL_RED;
    }

    /**
     * Returns the ApproachLane for a given direction.
     *
     * @param direction approach direction
     * @return corresponding ApproachLane
     */
    public ApproachLane getApproach(Direction direction) {
        return approaches.get(direction);
    }

    public TrafficPhase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Sets the active traffic phase.
     *
     * @param newPhase new operational phase
     */
    public void setPhase(TrafficPhase newPhase) {
        this.currentPhase = Objects.requireNonNull(newPhase, "Phase cannot be null");
    }

    /**
     * Retrieves the current signal color displayed to a specific direction.
     *
     * @param direction approach direction
     * @return current SignalColor (RED, YELLOW, GREEN)
     */
    public SignalColor getSignal(Direction direction) {
        return currentPhase.getSignal(direction);
    }

    /**
     * Steps the simulation of all 4 approaches forward in time.
     *
     * @param deltaSeconds   elapsed time in seconds
     * @param currentSimTime current global simulation timestamp
     */
    public void update(double deltaSeconds, double currentSimTime) {
        for (Direction dir : Direction.values()) {
            ApproachLane lane = approaches.get(dir);
            SignalColor signal = getSignal(dir);
            lane.update(deltaSeconds, signal, currentSimTime);
        }
    }

    /**
     * Computes the combined queue length across all 4 approaches.
     *
     * @return total stopped vehicles waiting in queues
     */
    public int getTotalQueueLength() {
        int total = 0;
        for (ApproachLane lane : approaches.values()) {
            total += lane.getQueueLength();
        }
        return total;
    }

    /**
     * Retrieves the queue length for a single approach.
     */
    public int getQueueLength(Direction direction) {
        return approaches.get(direction).getQueueLength();
    }

    /**
     * Retrieves the longest waiting time currently experienced by a stopped vehicle
     * in a given approach.
     */
    public double getMaxWaitTime(Direction direction) {
        return approaches.get(direction).getMaxWaitTime();
    }

    /**
     * Retrieves the total count of active vehicles currently present in all 4 approaches.
     */
    public int getTotalActiveVehicles() {
        int total = 0;
        for (ApproachLane lane : approaches.values()) {
            total += lane.getVehicles().size();
        }
        return total;
    }

    /**
     * Retrieves the total count of vehicles that have successfully cleared the intersection.
     */
    public int getTotalClearedVehicles() {
        int total = 0;
        for (ApproachLane lane : approaches.values()) {
            total += lane.getClearedVehicleCount();
        }
        return total;
    }

    /**
     * Aggregates all cleared vehicles from all four approaches.
     */
    public List<Vehicle> getAllClearedVehicles() {
        List<Vehicle> all = new ArrayList<>();
        for (ApproachLane lane : approaches.values()) {
            all.addAll(lane.getClearedVehicles());
        }
        return all;
    }

    /**
     * Clears all vehicles from all approaches and resets the phase to ALL_RED.
     */
    public void clear() {
        for (ApproachLane lane : approaches.values()) {
            lane.clear();
        }
        this.currentPhase = TrafficPhase.ALL_RED;
    }
}
