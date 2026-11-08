package com.trafficflow.config;

import com.trafficflow.model.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationConfigTest {

    @Test
    @DisplayName("Default configuration initializes with sensible defaults")
    void testDefaultConfig() {
        SimulationConfig config = new SimulationConfig();

        assertEquals(300.0, config.getSimulationDuration());
        assertEquals(0.1, config.getTimeStep());
        assertEquals(42L, config.getRandomSeed());
        assertEquals(10.0, config.getCruisingSpeed());
        assertEquals(2.5, config.getSafeGap());

        for (Direction dir : Direction.values()) {
            assertEquals(12.0, config.getArrivalRate(dir));
        }
    }

    @Test
    @DisplayName("Builder properly constructs custom configuration")
    void testBuilder() {
        SimulationConfig config = SimulationConfig.builder()
                .duration(600.0)
                .timeStep(0.05)
                .seed(999L)
                .cruisingSpeed(12.5)
                .safeGap(3.0)
                .arrivalRate(Direction.NORTH, 25.0)
                .arrivalRate(Direction.SOUTH, 20.0)
                .arrivalRate(Direction.EAST, 10.0)
                .arrivalRate(Direction.WEST, 5.0)
                .build();

        assertEquals(600.0, config.getSimulationDuration());
        assertEquals(0.05, config.getTimeStep());
        assertEquals(999L, config.getRandomSeed());
        assertEquals(12.5, config.getCruisingSpeed());
        assertEquals(3.0, config.getSafeGap());
        assertEquals(25.0, config.getArrivalRate(Direction.NORTH));
        assertEquals(20.0, config.getArrivalRate(Direction.SOUTH));
        assertEquals(10.0, config.getArrivalRate(Direction.EAST));
        assertEquals(5.0, config.getArrivalRate(Direction.WEST));
    }

    @Test
    @DisplayName("Rejects non-positive or non-finite simulation duration")
    void testInvalidDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().duration(0.0).build());
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().duration(-100.0).build());
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().duration(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().duration(Double.POSITIVE_INFINITY).build());
    }

    @Test
    @DisplayName("Rejects non-positive or non-finite time step")
    void testInvalidTimeStep() {
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().timeStep(0.0).build());
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().timeStep(-0.1).build());
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().timeStep(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().duration(10.0).timeStep(20.0).build());
    }

    @Test
    @DisplayName("Rejects negative or invalid arrival rates")
    void testInvalidArrivalRates() {
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().arrivalRate(Direction.NORTH, -5.0).build());
        assertThrows(IllegalArgumentException.class, () ->
                SimulationConfig.builder().arrivalRate(Direction.EAST, Double.NaN).build());

        Map<Direction, Double> incomplete = new EnumMap<>(Direction.class);
        incomplete.put(Direction.NORTH, 10.0);
        assertThrows(IllegalArgumentException.class, () ->
                new SimulationConfig(100.0, 0.1, incomplete, 42L, 10.0, 2.5));
    }

    @Test
    @DisplayName("Scenario presets produce valid configurations with expected distributions")
    void testScenarioPresets() {
        for (ScenarioPreset preset : ScenarioPreset.values()) {
            SimulationConfig config = preset.toConfig(12345L);
            assertNotNull(config);
            assertEquals(12345L, config.getRandomSeed());
            assertNotNull(preset.getDisplayName());
            assertNotNull(preset.getDescription());

            for (Direction dir : Direction.values()) {
                assertTrue(config.getArrivalRate(dir) >= 0.0);
            }
        }

        // Verify specific preset properties
        SimulationConfig nsHeavy = ScenarioPreset.NORTH_SOUTH_HEAVY.toConfig();
        assertTrue(nsHeavy.getArrivalRate(Direction.NORTH) > nsHeavy.getArrivalRate(Direction.EAST));
        assertTrue(nsHeavy.getArrivalRate(Direction.SOUTH) > nsHeavy.getArrivalRate(Direction.WEST));

        SimulationConfig ewHeavy = ScenarioPreset.EAST_WEST_HEAVY.toConfig();
        assertTrue(ewHeavy.getArrivalRate(Direction.EAST) > ewHeavy.getArrivalRate(Direction.NORTH));
        assertTrue(ewHeavy.getArrivalRate(Direction.WEST) > ewHeavy.getArrivalRate(Direction.SOUTH));
    }
}
