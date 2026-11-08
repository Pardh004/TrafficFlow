package com.trafficflow.engine;

import com.trafficflow.config.SimulationConfig;
import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.Vehicle;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Deterministic vehicle generator that creates vehicle arrivals according to configured arrival rates.
 *
 * Guarantees:
 *  - 100% deterministic, repeatable behavior given identical seeds and time-step sequences.
 *  - Supports distinct arrival rates for all four approaches (NORTH, SOUTH, EAST, WEST).
 *  - Respects approach entrance clearance so vehicles do not overlap on spawn.
 */
public class VehicleSpawner {

    private final long seed;
    private final Random random;
    private final Map<Direction, Double> arrivalRates = new EnumMap<>(Direction.class);
    private final double cruisingSpeed;

    private int nextVehicleId = 1;
    private int totalVehiclesGenerated = 0;
    private int totalSpawnsBlocked = 0;

    /**
     * Constructs a VehicleSpawner based on the provided SimulationConfig.
     *
     * @param config validated simulation configuration
     */
    public VehicleSpawner(SimulationConfig config) {
        Objects.requireNonNull(config, "SimulationConfig cannot be null");
        this.seed = config.getRandomSeed();
        this.random = new Random(seed);
        this.arrivalRates.putAll(config.getArrivalRates());
        this.cruisingSpeed = config.getCruisingSpeed();
    }

    /**
     * Constructs a VehicleSpawner with explicit rates and seed.
     *
     * @param seed          random seed for deterministic generation
     * @param arrivalRates  map of arrival rates (vehicles/minute) per direction
     * @param cruisingSpeed default cruising speed for new vehicles (m/s)
     */
    public VehicleSpawner(long seed, Map<Direction, Double> arrivalRates, double cruisingSpeed) {
        this.seed = seed;
        this.random = new Random(seed);
        this.cruisingSpeed = cruisingSpeed;

        Objects.requireNonNull(arrivalRates, "Arrival rates cannot be null");
        for (Direction dir : Direction.values()) {
            Double rate = arrivalRates.get(dir);
            if (rate == null || rate < 0.0) {
                throw new IllegalArgumentException("Invalid arrival rate for " + dir + ": " + rate);
            }
            this.arrivalRates.put(dir, rate);
        }
    }

    /**
     * Calculates the exact Poisson arrival probability of at least one arrival occurring
     * within the discrete time window deltaSeconds for a given arrival rate (veh/min):
     *   P(N >= 1) = 1.0 - exp(-(ratePerMinute / 60.0) * deltaSeconds)
     *
     * Note: In this discrete simulation, at most one vehicle can physically spawn per approach per tick,
     * as multiple simultaneous arrivals at the same entrance point (position 0.0) would physically overlap.
     *
     * @param ratePerMinute arrival rate in vehicles per minute
     * @param deltaSeconds  discrete time step duration in seconds
     * @return arrival probability bounded strictly within [0.0, 1.0]
     */
    static double calculateArrivalProbability(double ratePerMinute, double deltaSeconds) {
        if (!Double.isFinite(ratePerMinute) || ratePerMinute <= 0.0 ||
                !Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
            return 0.0;
        }
        double mu = (ratePerMinute / 60.0) * deltaSeconds;
        double p = 1.0 - Math.exp(-mu);
        if (p < 0.0) {
            return 0.0;
        }
        if (p > 1.0 || Double.isNaN(p)) {
            return 1.0;
        }
        return p;
    }

    /**
     * Evaluates stochastic arrival probabilities for each approach in fixed directional order.
     * Generates and adds vehicles to the intersection lanes if entrance space is clear.
     * In this discrete simulation, at most one vehicle can physically spawn per approach per tick.
     *
     * @param deltaSeconds   time step in seconds
     * @param currentSimTime current global simulation timestamp
     * @param intersection   target intersection to spawn vehicles into
     * @return number of new vehicles successfully spawned this tick
     */
    public int step(double deltaSeconds, double currentSimTime, Intersection intersection) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
            return 0;
        }
        Objects.requireNonNull(intersection, "Intersection cannot be null");

        int spawnedThisStep = 0;

        // Strict iteration order (NORTH, SOUTH, EAST, WEST) guarantees deterministic sequence
        for (Direction dir : Direction.values()) {
            double ratePerMin = arrivalRates.getOrDefault(dir, 0.0);
            if (ratePerMin <= 0.0) {
                continue;
            }

            // Exact Poisson arrival probability: P(N >= 1) = 1.0 - exp(-mu)
            double arrivalProbability = calculateArrivalProbability(ratePerMin, deltaSeconds);
            if (arrivalProbability <= 0.0) {
                continue;
            }

            if (random.nextDouble() < arrivalProbability) {
                // Arrival event triggered: check if approach entrance can accept vehicle
                var approachLane = intersection.getApproach(dir);
                if (approachLane.canSpawnVehicle()) {
                    Vehicle vehicle = new Vehicle(nextVehicleId++, dir, currentSimTime, cruisingSpeed);
                    if (approachLane.tryAddVehicle(vehicle)) {
                        totalVehiclesGenerated++;
                        spawnedThisStep++;
                    } else {
                        totalSpawnsBlocked++;
                    }
                } else {
                    totalSpawnsBlocked++;
                }
            }
        }

        return spawnedThisStep;
    }

    /**
     * Resets the spawner to its initial state, resetting the pseudo-random generator
     * to the original seed and clearing counters.
     */
    public void reset() {
        random.setSeed(seed);
        nextVehicleId = 1;
        totalVehiclesGenerated = 0;
        totalSpawnsBlocked = 0;
    }

    public long getSeed() {
        return seed;
    }

    public int getTotalVehiclesGenerated() {
        return totalVehiclesGenerated;
    }

    public int getTotalSpawnsBlocked() {
        return totalSpawnsBlocked;
    }

    public Map<Direction, Double> getArrivalRates() {
        return Collections.unmodifiableMap(arrivalRates);
    }

    public double getArrivalRate(Direction direction) {
        return arrivalRates.getOrDefault(direction, 0.0);
    }

    public void setArrivalRate(Direction direction, double ratePerMinute) {
        if (!Double.isFinite(ratePerMinute) || ratePerMinute < 0.0) {
            throw new IllegalArgumentException("Arrival rate must be non-negative and finite");
        }
        arrivalRates.put(Objects.requireNonNull(direction), ratePerMinute);
    }
}
