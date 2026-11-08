package com.trafficflow.config;

import com.trafficflow.model.Direction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration parameters for a traffic simulation run.
 * Ensures all timing, arrival rates, physical boundaries, and random seeds are validated.
 */
public class SimulationConfig {

    public static final double DEFAULT_DURATION = 300.0;       // 5 minutes in simulation seconds
    public static final double DEFAULT_TIME_STEP = 0.1;         // 100 milliseconds internal physics step
    public static final double DEFAULT_CRUISING_SPEED = 10.0;   // 10.0 m/s = 36 km/h
    public static final double DEFAULT_SAFE_GAP = 2.5;          // 2.5 meters bumper-to-bumper buffer
    public static final long DEFAULT_SEED = 42L;

    private final double simulationDuration;
    private final double timeStep;
    private final Map<Direction, Double> arrivalRates; // In vehicles per minute per direction
    private final long randomSeed;
    private final double cruisingSpeed;
    private final double safeGap;

    public SimulationConfig(double simulationDuration,
                            double timeStep,
                            Map<Direction, Double> arrivalRates,
                            long randomSeed,
                            double cruisingSpeed,
                            double safeGap) {
        if (!Double.isFinite(simulationDuration) || simulationDuration <= 0.0) {
            throw new IllegalArgumentException("Simulation duration must be positive and finite: " + simulationDuration);
        }
        if (!Double.isFinite(timeStep) || timeStep <= 0.0) {
            throw new IllegalArgumentException("Time step must be positive and finite: " + timeStep);
        }
        if (timeStep > simulationDuration) {
            throw new IllegalArgumentException("Time step (" + timeStep + ") cannot exceed simulation duration (" + simulationDuration + ")");
        }
        if (!Double.isFinite(cruisingSpeed) || cruisingSpeed <= 0.0) {
            throw new IllegalArgumentException("Cruising speed must be positive and finite: " + cruisingSpeed);
        }
        if (!Double.isFinite(safeGap) || safeGap < 0.0) {
            throw new IllegalArgumentException("Safe gap cannot be negative or non-finite: " + safeGap);
        }

        Objects.requireNonNull(arrivalRates, "Arrival rates map cannot be null");
        Map<Direction, Double> rates = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            Double rate = arrivalRates.get(dir);
            if (rate == null) {
                throw new IllegalArgumentException("Missing arrival rate for direction: " + dir);
            }
            if (!Double.isFinite(rate) || rate < 0.0) {
                throw new IllegalArgumentException("Arrival rate for " + dir + " must be non-negative and finite: " + rate);
            }
            rates.put(dir, rate);
        }

        this.simulationDuration = simulationDuration;
        this.timeStep = timeStep;
        this.arrivalRates = Collections.unmodifiableMap(rates);
        this.randomSeed = randomSeed;
        this.cruisingSpeed = cruisingSpeed;
        this.safeGap = safeGap;
    }

    /**
     * Helper constructor with uniform arrival rate on all directions.
     */
    public SimulationConfig(double simulationDuration, double timeStep, double uniformRatePerMinute, long randomSeed) {
        this(simulationDuration, timeStep, createUniformRates(uniformRatePerMinute), randomSeed, DEFAULT_CRUISING_SPEED, DEFAULT_SAFE_GAP);
    }

    /**
     * Default configuration.
     */
    public SimulationConfig() {
        this(DEFAULT_DURATION, DEFAULT_TIME_STEP, createUniformRates(12.0), DEFAULT_SEED, DEFAULT_CRUISING_SPEED, DEFAULT_SAFE_GAP);
    }

    public static Map<Direction, Double> createUniformRates(double ratePerMinute) {
        Map<Direction, Double> rates = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            rates.put(dir, ratePerMinute);
        }
        return rates;
    }

    public double getSimulationDuration() {
        return simulationDuration;
    }

    public double getTimeStep() {
        return timeStep;
    }

    public Map<Direction, Double> getArrivalRates() {
        return arrivalRates;
    }

    public double getArrivalRate(Direction direction) {
        return arrivalRates.get(direction);
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public double getCruisingSpeed() {
        return cruisingSpeed;
    }

    public double getSafeGap() {
        return safeGap;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double duration = DEFAULT_DURATION;
        private double timeStep = DEFAULT_TIME_STEP;
        private final Map<Direction, Double> arrivalRates = new EnumMap<>(Direction.class);
        private long seed = DEFAULT_SEED;
        private double cruisingSpeed = DEFAULT_CRUISING_SPEED;
        private double safeGap = DEFAULT_SAFE_GAP;

        public Builder() {
            for (Direction dir : Direction.values()) {
                arrivalRates.put(dir, 12.0); // Default 12 veh/min
            }
        }

        public Builder duration(double duration) {
            this.duration = duration;
            return this;
        }

        public Builder timeStep(double timeStep) {
            this.timeStep = timeStep;
            return this;
        }

        public Builder arrivalRate(Direction direction, double ratePerMinute) {
            this.arrivalRates.put(Objects.requireNonNull(direction), ratePerMinute);
            return this;
        }

        public Builder arrivalRates(Map<Direction, Double> rates) {
            this.arrivalRates.putAll(Objects.requireNonNull(rates));
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder cruisingSpeed(double cruisingSpeed) {
            this.cruisingSpeed = cruisingSpeed;
            return this;
        }

        public Builder safeGap(double safeGap) {
            this.safeGap = safeGap;
            return this;
        }

        public SimulationConfig build() {
            return new SimulationConfig(duration, timeStep, arrivalRates, seed, cruisingSpeed, safeGap);
        }
    }
}
