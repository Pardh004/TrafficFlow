package com.trafficflow.model;

import java.util.Objects;

/**
 * Represents an individual vehicle traversing an intersection approach.
 * Uses simple, consistent kinematics (constant speed when clear, stopped when obstructed)
 * to maintain clarity, repeatability, and educational transparency.
 */
public class Vehicle {

    private final int id;
    private final Direction origin;
    private final double spawnTime; // In simulation seconds
    private final double length;    // Vehicle length in meters (e.g., 4.5m)

    private double position;        // Position in meters from the approach start (0.0) toward the intersection
    private double speed;           // Current speed in meters per second
    private double waitTime;        // Cumulative seconds spent completely stopped (speed == 0)
    private boolean passedStopLine; // True once vehicle front has crossed the stop line
    private Double departureTime;   // Timestamp when vehicle fully cleared the intersection

    /**
     * Constructs a new vehicle at the entrance of an approach.
     *
     * @param id          unique vehicle identifier
     * @param origin      direction of origin
     * @param spawnTime   simulation time when created
     * @param targetSpeed initial cruising speed in m/s
     * @param length      vehicle length in meters
     */
    public Vehicle(int id, Direction origin, double spawnTime, double targetSpeed, double length) {
        if (targetSpeed < 0) {
            throw new IllegalArgumentException("Target speed cannot be negative");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Vehicle length must be positive");
        }
        this.id = id;
        this.origin = Objects.requireNonNull(origin, "Origin direction cannot be null");
        this.spawnTime = spawnTime;
        this.length = length;
        this.position = 0.0;
        this.speed = targetSpeed;
        this.waitTime = 0.0;
        this.passedStopLine = false;
        this.departureTime = null;
    }

    /**
     * Convenience constructor with default standard vehicle length (4.5 meters).
     */
    public Vehicle(int id, Direction origin, double spawnTime, double targetSpeed) {
        this(id, origin, spawnTime, targetSpeed, 4.5);
    }

    /**
     * Advances the vehicle by deltaSeconds.
     * If stopped, accumulates waiting time.
     * If moving, updates position along the lane.
     *
     * @param deltaSeconds elapsed simulation time in seconds
     */
    public void step(double deltaSeconds) {
        if (deltaSeconds <= 0) {
            return;
        }

        if (isStopped()) {
            waitTime += deltaSeconds;
        } else {
            position += speed * deltaSeconds;
        }
    }

    /**
     * A vehicle is considered stopped if its current speed is effectively zero.
     *
     * @return true if stopped, false if moving
     */
    public boolean isStopped() {
        return speed <= 0.001;
    }

    public int getId() {
        return id;
    }

    public Direction getOrigin() {
        return origin;
    }

    public double getSpawnTime() {
        return spawnTime;
    }

    public double getLength() {
        return length;
    }

    public double getPosition() {
        return position;
    }

    public void setPosition(double position) {
        this.position = position;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = Math.max(0.0, speed);
    }

    public double getWaitTime() {
        return waitTime;
    }

    public boolean hasPassedStopLine() {
        return passedStopLine;
    }

    public void setPassedStopLine(boolean passedStopLine) {
        this.passedStopLine = passedStopLine;
    }

    public Double getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(double departureTime) {
        this.departureTime = departureTime;
    }

    /**
     * Computes the total time the vehicle has spent in the system.
     *
     * @param currentSimTime current simulation time
     * @return elapsed system time in seconds
     */
    public double getTotalSystemTime(double currentSimTime) {
        if (departureTime != null) {
            return departureTime - spawnTime;
        }
        return Math.max(0.0, currentSimTime - spawnTime);
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "id=" + id +
                ", origin=" + origin +
                ", pos=" + String.format("%.1f", position) +
                "m, spd=" + String.format("%.1f", speed) +
                "m/s, wait=" + String.format("%.1f", waitTime) +
                "s}";
    }
}
