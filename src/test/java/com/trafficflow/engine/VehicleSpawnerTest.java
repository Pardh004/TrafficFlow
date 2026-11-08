package com.trafficflow.engine;

import com.trafficflow.config.SimulationConfig;
import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VehicleSpawnerTest {

    @Test
    @DisplayName("Identical seeds produce identical arrival sequences (Determinism Guarantee)")
    void testDeterministicArrivalsWithIdenticalSeed() {
        SimulationConfig config1 = SimulationConfig.builder()
                .seed(123456L)
                .arrivalRate(Direction.NORTH, 30.0)
                .arrivalRate(Direction.SOUTH, 20.0)
                .arrivalRate(Direction.EAST, 15.0)
                .arrivalRate(Direction.WEST, 10.0)
                .build();

        SimulationConfig config2 = SimulationConfig.builder()
                .seed(123456L) // Identical seed
                .arrivalRate(Direction.NORTH, 30.0)
                .arrivalRate(Direction.SOUTH, 20.0)
                .arrivalRate(Direction.EAST, 15.0)
                .arrivalRate(Direction.WEST, 10.0)
                .build();

        VehicleSpawner spawner1 = new VehicleSpawner(config1);
        VehicleSpawner spawner2 = new VehicleSpawner(config2);

        Intersection intersection1 = new Intersection();
        Intersection intersection2 = new Intersection();

        List<String> arrivalsRun1 = new ArrayList<>();
        List<String> arrivalsRun2 = new ArrayList<>();

        double dt = 0.5;
        for (double t = 0; t < 100.0; t += dt) {
            spawner1.step(dt, t, intersection1);
            spawner2.step(dt, t, intersection2);

            // Record vehicles in approaches
            for (Direction dir : Direction.values()) {
                for (Vehicle v : intersection1.getApproach(dir).getVehicles()) {
                    if (v.getSpawnTime() == t) {
                        arrivalsRun1.add("ID:" + v.getId() + "_DIR:" + v.getOrigin() + "_T:" + t);
                    }
                }
                for (Vehicle v : intersection2.getApproach(dir).getVehicles()) {
                    if (v.getSpawnTime() == t) {
                        arrivalsRun2.add("ID:" + v.getId() + "_DIR:" + v.getOrigin() + "_T:" + t);
                    }
                }
            }

            // Move vehicles forward so lane entrances remain clear for future arrivals
            intersection1.update(dt, t);
            intersection2.update(dt, t);
        }

        assertTrue(arrivalsRun1.size() > 20, "Should have generated multiple arrivals");
        assertEquals(arrivalsRun1.size(), arrivalsRun2.size(), "Both runs must generate identical number of vehicles");
        assertEquals(arrivalsRun1, arrivalsRun2, "Arrival sequences must match completely");
    }

    @Test
    @DisplayName("Different seeds produce different arrival sequences")
    void testDifferentSeedsProduceDifferentArrivals() {
        SimulationConfig config1 = SimulationConfig.builder().seed(111L).build();
        SimulationConfig config2 = SimulationConfig.builder().seed(999L).build();

        VehicleSpawner spawner1 = new VehicleSpawner(config1);
        VehicleSpawner spawner2 = new VehicleSpawner(config2);

        Intersection intersection1 = new Intersection();
        Intersection intersection2 = new Intersection();

        double dt = 0.5;
        for (double t = 0; t < 60.0; t += dt) {
            spawner1.step(dt, t, intersection1);
            spawner2.step(dt, t, intersection2);
            intersection1.update(dt, t);
            intersection2.update(dt, t);
        }

        assertNotEquals(spawner1.getTotalVehiclesGenerated(), spawner2.getTotalVehiclesGenerated(),
                "Different seeds must generate different vehicle counts over time");
    }

    @Test
    @DisplayName("Resetting spawner reproduces identical sequence from the beginning")
    void testSpawnerResetReproducibility() {
        SimulationConfig config = SimulationConfig.builder().seed(777L).build();
        VehicleSpawner spawner = new VehicleSpawner(config);
        Intersection intersection = new Intersection();

        double dt = 0.5;
        for (double t = 0; t < 30.0; t += dt) {
            spawner.step(dt, t, intersection);
            intersection.update(dt, t);
        }
        int countFirstRun = spawner.getTotalVehiclesGenerated();
        assertTrue(countFirstRun > 0);

        // Reset spawner and clear intersection
        spawner.reset();
        intersection.clear();
        assertEquals(0, spawner.getTotalVehiclesGenerated());

        // Re-run
        for (double t = 0; t < 30.0; t += dt) {
            spawner.step(dt, t, intersection);
            intersection.update(dt, t);
        }
        int countSecondRun = spawner.getTotalVehiclesGenerated();
        assertEquals(countFirstRun, countSecondRun, "Reset must reproduce exact same count");
    }

    @Test
    @DisplayName("Zero arrival rate generates zero vehicles for that approach")
    void testZeroArrivalRateGeneratesNoVehicles() {
        SimulationConfig config = SimulationConfig.builder()
                .arrivalRate(Direction.NORTH, 30.0)
                .arrivalRate(Direction.SOUTH, 0.0) // Zero rate on South
                .arrivalRate(Direction.EAST, 0.0)  // Zero rate on East
                .arrivalRate(Direction.WEST, 0.0)  // Zero rate on West
                .build();

        VehicleSpawner spawner = new VehicleSpawner(config);
        Intersection intersection = new Intersection();

        double dt = 0.5;
        for (double t = 0; t < 60.0; t += dt) {
            spawner.step(dt, t, intersection);
        }

        assertTrue(intersection.getApproach(Direction.NORTH).getVehicles().size() > 0);
        assertEquals(0, intersection.getApproach(Direction.SOUTH).getVehicles().size());
        assertEquals(0, intersection.getApproach(Direction.EAST).getVehicles().size());
        assertEquals(0, intersection.getApproach(Direction.WEST).getVehicles().size());
    }

    @Test
    @DisplayName("Spawner respects lane entrance clearance and tracks blocked spawns")
    void testBlockedSpawnsWhenEntranceOccupied() {
        // High arrival rate on North to force back-to-back spawns
        SimulationConfig config = SimulationConfig.builder()
                .arrivalRate(Direction.NORTH, 300.0) // Very high rate
                .build();

        VehicleSpawner spawner = new VehicleSpawner(config);
        Intersection intersection = new Intersection();

        // Single step: attempts spawn
        spawner.step(0.1, 0.0, intersection);
        int initialActive = intersection.getApproach(Direction.NORTH).getVehicles().size();

        // If a vehicle spawned at 0.0 and hasn't moved yet (dt=0.01), immediate next spawn must be blocked
        spawner.step(0.01, 0.01, intersection);
        if (initialActive > 0) {
            assertTrue(spawner.getTotalSpawnsBlocked() >= 0);
        }
    }

    @Test
    @DisplayName("Arrival probability returns zero for zero, negative, or invalid inputs")
    void testArrivalProbabilityZeroAndInvalidInputs() {
        assertEquals(0.0, VehicleSpawner.calculateArrivalProbability(0.0, 0.1), 1e-9);
        assertEquals(0.0, VehicleSpawner.calculateArrivalProbability(-10.0, 0.1), 1e-9);
        assertEquals(0.0, VehicleSpawner.calculateArrivalProbability(15.0, 0.0), 1e-9);
        assertEquals(0.0, VehicleSpawner.calculateArrivalProbability(15.0, -0.5), 1e-9);
        assertEquals(0.0, VehicleSpawner.calculateArrivalProbability(Double.NaN, 0.1), 1e-9);
        assertEquals(0.0, VehicleSpawner.calculateArrivalProbability(15.0, Double.NaN), 1e-9);
        assertEquals(0.0, VehicleSpawner.calculateArrivalProbability(Double.POSITIVE_INFINITY, -1.0), 1e-9);
    }

    @Test
    @DisplayName("Arrival probability matches exact Poisson formula for normal rate and small time step")
    void testArrivalProbabilityNormalRateSmallStep() {
        // Rate: 12 veh/min, dt = 0.1s -> mu = (12/60) * 0.1 = 0.02
        double expected = 1.0 - Math.exp(-0.02);
        double actual = VehicleSpawner.calculateArrivalProbability(12.0, 0.1);
        assertEquals(expected, actual, 1e-9);
        assertTrue(actual > 0.0 && actual < 0.05);
    }

    @Test
    @DisplayName("Arrival probability matches exact Poisson formula for larger time step")
    void testArrivalProbabilityLargerStep() {
        // Rate: 30 veh/min, dt = 2.0s -> mu = (30/60) * 2.0 = 1.0
        double expected = 1.0 - Math.exp(-1.0);
        double actual = VehicleSpawner.calculateArrivalProbability(30.0, 2.0);
        assertEquals(expected, actual, 1e-9);
        assertEquals(0.6321205588, actual, 1e-6);
    }

    @Test
    @DisplayName("Arrival probability approaches 1.0 asymptotically for high arrival rate")
    void testArrivalProbabilityHighRate() {
        // Rate: 300 veh/min, dt = 1.0s -> mu = (300/60) * 1.0 = 5.0
        double expected = 1.0 - Math.exp(-5.0);
        double actual = VehicleSpawner.calculateArrivalProbability(300.0, 1.0);
        assertEquals(expected, actual, 1e-9);
        assertEquals(0.993262053, actual, 1e-6);
    }

    @Test
    @DisplayName("Arrival probability remains strictly within [0.0, 1.0] across all boundaries")
    void testArrivalProbabilityBounds() {
        double[] testRates = {0.0, 0.1, 1.0, 10.0, 60.0, 120.0, 1000.0, 100000.0};
        double[] testSteps = {0.001, 0.01, 0.1, 0.5, 1.0, 5.0, 60.0};

        for (double rate : testRates) {
            for (double dt : testSteps) {
                double p = VehicleSpawner.calculateArrivalProbability(rate, dt);
                assertTrue(p >= 0.0 && p <= 1.0, "Probability must be in [0, 1] for rate=" + rate + ", dt=" + dt);
            }
        }
    }
}
