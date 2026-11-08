package com.trafficflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Manages vehicles traveling along a single approach toward the intersection.
 * Enforces:
 *  - Stop-line adherence based on signal state (RED / YELLOW / GREEN).
 *  - Safe bumper-to-bumper vehicle spacing (anti-collision).
 *  - Consistent, transparent kinematics without unnecessary physics complexity.
 *  - Queue and waiting time tracking for adaptive traffic scheduling.
 */
public class ApproachLane {

    private final Direction direction;
    private final double laneLength;           // Total length in meters (e.g., 200m)
    private final double stopLinePosition;      // Stop line distance from entrance (e.g., 150m)
    private final double safeGap;               // Minimum bumper-to-bumper buffer (e.g., 2.5m)
    private final double defaultCruisingSpeed;  // Standard speed in m/s (e.g., 10.0 m/s = 36 km/h)

    // Vehicles ordered from front (closest to/beyond stop line) to back (nearest to spawn)
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final List<Vehicle> clearedVehicles = new ArrayList<>();

    /**
     * Constructs an approach lane with custom geometry.
     *
     * @param direction            compass direction of approach
     * @param laneLength           total road length in meters
     * @param stopLinePosition     distance from start to the stop line in meters
     * @param safeGap              minimum buffer between vehicles in meters
     * @param defaultCruisingSpeed standard travel speed in m/s
     */
    public ApproachLane(Direction direction,
                        double laneLength,
                        double stopLinePosition,
                        double safeGap,
                        double defaultCruisingSpeed) {
        if (stopLinePosition <= 0 || stopLinePosition >= laneLength) {
            throw new IllegalArgumentException("Stop line must be strictly between 0 and laneLength");
        }
        if (safeGap < 0) {
            throw new IllegalArgumentException("Safe gap cannot be negative");
        }
        if (defaultCruisingSpeed <= 0) {
            throw new IllegalArgumentException("Cruising speed must be positive");
        }
        this.direction = Objects.requireNonNull(direction, "Direction cannot be null");
        this.laneLength = laneLength;
        this.stopLinePosition = stopLinePosition;
        this.safeGap = safeGap;
        this.defaultCruisingSpeed = defaultCruisingSpeed;
    }

    /**
     * Convenience constructor with default city street dimensions:
     * 200m total road, stop line at 150m, 2.5m safe gap, 10.0 m/s (~36 km/h) cruising speed.
     */
    public ApproachLane(Direction direction) {
        this(direction, 200.0, 150.0, 2.5, 10.0);
    }

    /**
     * Checks if a new vehicle can safely spawn at the entrance without overlapping an existing vehicle.
     *
     * @return true if space is available at position 0.0, false otherwise
     */
    public boolean canSpawnVehicle() {
        if (vehicles.isEmpty()) {
            return true;
        }
        Vehicle lastVehicle = vehicles.get(vehicles.size() - 1);
        double rearBumper = lastVehicle.getPosition() - lastVehicle.getLength();
        return rearBumper >= safeGap;
    }

    /**
     * Spawns a vehicle into this lane if entrance space allows.
     *
     * @param vehicle vehicle to add
     * @return true if successfully added, false if entrance was blocked
     */
    public boolean tryAddVehicle(Vehicle vehicle) {
        if (!canSpawnVehicle()) {
            return false;
        }
        vehicle.setPosition(0.0);
        vehicle.setSpeed(defaultCruisingSpeed);
        vehicles.add(vehicle);
        return true;
    }

    /**
     * Inserts an existing vehicle directly into this lane.
     * Useful for scenario initialization, queue preloading, and unit testing.
     *
     * @param vehicle vehicle to insert
     */
    public void insertVehicle(Vehicle vehicle) {
        vehicles.add(Objects.requireNonNull(vehicle, "Vehicle cannot be null"));
    }

    /**
     * Updates positions, queues, and wait times for all vehicles on this lane.
     *
     * @param deltaSeconds  elapsed time in seconds
     * @param currentSignal current signal state for this approach
     * @param currentSimTime current global simulation timestamp
     */
    public void update(double deltaSeconds, SignalColor currentSignal, double currentSimTime) {
        if (deltaSeconds <= 0) {
            return;
        }

        // Process vehicles from front to back to maintain safe following distances
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle current = vehicles.get(i);

            // Determine maximum position allowed for this vehicle
            double maxAllowedPosition;

            if (i == 0) {
                // Lead vehicle: constrained only by the stop line when signal is not green
                if (!current.hasPassedStopLine()) {
                    if (currentSignal.mustStop() || currentSignal.isCaution()) {
                        maxAllowedPosition = stopLinePosition;
                    } else {
                        // Green: proceed through intersection
                        maxAllowedPosition = laneLength + 100.0;
                    }
                } else {
                    // Already entered intersection: free to clear
                    maxAllowedPosition = laneLength + 100.0;
                }
            } else {
                // Trailing vehicle: constrained by the vehicle immediately ahead
                Vehicle leader = vehicles.get(i - 1);
                double leaderRearBumper = leader.getPosition() - leader.getLength();
                double followerMaxAhead = leaderRearBumper - safeGap;

                if (!current.hasPassedStopLine() && (currentSignal.mustStop() || currentSignal.isCaution())) {
                    // Also bounded by stop line if light is red/yellow
                    maxAllowedPosition = Math.min(stopLinePosition, followerMaxAhead);
                } else {
                    maxAllowedPosition = followerMaxAhead;
                }
            }

            // Update movement or stopped state
            double distanceToMax = maxAllowedPosition - current.getPosition();

            if (distanceToMax <= 0.001) {
                // Already obstructed / stationary: remain stopped and accumulate wait time
                current.setSpeed(0.0);
                current.step(deltaSeconds);
            } else {
                double stepDistance = defaultCruisingSpeed * deltaSeconds;
                if (stepDistance >= distanceToMax) {
                    // Reached boundary during this interval: vehicle halts at boundary;
                    // waiting time begins accumulating on the subsequent stationary tick
                    current.setPosition(maxAllowedPosition);
                    current.setSpeed(0.0);
                } else {
                    // Unimpeded forward movement
                    current.setSpeed(defaultCruisingSpeed);
                    current.step(deltaSeconds);
                }
            }

            // Check if vehicle has actually crossed beyond the stop line into the intersection
            if (!current.hasPassedStopLine() && current.getPosition() > stopLinePosition) {
                current.setPassedStopLine(true);
            }
        }

        // Clean up vehicles that have completely left the lane
        for (int i = vehicles.size() - 1; i >= 0; i--) {
            Vehicle v = vehicles.get(i);
            if (v.getPosition() - v.getLength() >= laneLength) {
                v.setDepartureTime(currentSimTime);
                clearedVehicles.add(v);
                vehicles.remove(i);
            }
        }
    }

    /**
     * Counts how many vehicles are currently stopped in queue waiting at or upstream of the stop line.
     *
     * @return number of queued vehicles
     */
    public int getQueueLength() {
        int count = 0;
        for (Vehicle v : vehicles) {
            if (!v.hasPassedStopLine() && v.isStopped()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the maximum waiting time among vehicles currently stopped in the queue.
     * Essential for the adaptive fairness algorithm to prevent vehicle starvation.
     *
     * @return maximum waiting time in seconds, or 0.0 if no vehicles are stopped
     */
    public double getMaxWaitTime() {
        double maxWait = 0.0;
        for (Vehicle v : vehicles) {
            if (!v.hasPassedStopLine() && v.isStopped()) {
                if (v.getWaitTime() > maxWait) {
                    maxWait = v.getWaitTime();
                }
            }
        }
        return maxWait;
    }

    /**
     * Returns an unmodifiable view of active vehicles on this lane.
     */
    public List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    /**
     * Returns the total count of vehicles that have completed passage through this lane.
     */
    public int getClearedVehicleCount() {
        return clearedVehicles.size();
    }

    /**
     * Returns an unmodifiable view of cleared vehicles.
     */
    public List<Vehicle> getClearedVehicles() {
        return Collections.unmodifiableList(clearedVehicles);
    }

    /**
     * Clears all active and cleared vehicles from this lane.
     */
    public void clear() {
        vehicles.clear();
        clearedVehicles.clear();
    }

    public Direction getDirection() {
        return direction;
    }

    public double getLaneLength() {
        return laneLength;
    }

    public double getStopLinePosition() {
        return stopLinePosition;
    }

    public double getSafeGap() {
        return safeGap;
    }

    public double getDefaultCruisingSpeed() {
        return defaultCruisingSpeed;
    }
}
