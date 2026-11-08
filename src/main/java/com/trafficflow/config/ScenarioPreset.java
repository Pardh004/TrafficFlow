package com.trafficflow.config;

import com.trafficflow.model.Direction;

import java.util.EnumMap;
import java.util.Map;

/**
 * Predefined traffic scenario presets for benchmarking and comparing signal controllers.
 * Note: These configurations use illustrative educational simulation parameters
 * designed to demonstrate queue dynamics and controller adaptability, not empirically
 * measured real-world traffic counts.
 */
public enum ScenarioPreset {

    /**
     * Balanced, moderate traffic across all four approaches.
     * Rate: 15 vehicles/min on North, South, East, and West (~1 vehicle every 4 seconds per direction).
     */
    BALANCED_TRAFFIC("Balanced Traffic", "Symmetric moderate flow across all four approaches (15 veh/min each)", 15.0, 15.0, 15.0, 15.0),

    /**
     * Asymmetric corridor: heavy arterial flow along North-South, light crossing flow along East-West.
     * Rate: 30 veh/min North/South, 6 veh/min East/West.
     */
    NORTH_SOUTH_HEAVY("North-South Heavy", "Heavy arterial corridor on North-South (30 veh/min) vs light East-West (6 veh/min)", 30.0, 30.0, 6.0, 6.0),

    /**
     * Asymmetric corridor: heavy arterial flow along East-West, light crossing flow along North-South.
     * Rate: 6 veh/min North/South, 30 veh/min East/West.
     */
    EAST_WEST_HEAVY("East-West Heavy", "Heavy arterial corridor on East-West (30 veh/min) vs light North-South (6 veh/min)", 6.0, 6.0, 30.0, 30.0),

    /**
     * Rush hour peak conditions: high volume on all approaches with arterial asymmetry.
     * Rate: 36 veh/min North, 30 veh/min South, 18 veh/min East, 15 veh/min West.
     */
    RUSH_HOUR("Rush Hour Congestion", "Peak traffic stress test with high density on all approaches (15-36 veh/min)", 36.0, 30.0, 18.0, 15.0),

    /**
     * Varied stochastic traffic with irregular distribution across approaches.
     * Rate: 22 veh/min North, 10 veh/min South, 18 veh/min East, 8 veh/min West.
     */
    RANDOM_TRAFFIC("Random Asymmetric Traffic", "Irregular demand distribution across all approaches (8-22 veh/min)", 22.0, 10.0, 18.0, 8.0);

    private final String displayName;
    private final String description;
    private final double northRate;
    private final double southRate;
    private final double eastRate;
    private final double westRate;

    ScenarioPreset(String displayName, String description, double northRate, double southRate, double eastRate, double westRate) {
        this.displayName = displayName;
        this.description = description;
        this.northRate = northRate;
        this.southRate = southRate;
        this.eastRate = eastRate;
        this.westRate = westRate;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Map<Direction, Double> getArrivalRates() {
        Map<Direction, Double> rates = new EnumMap<>(Direction.class);
        rates.put(Direction.NORTH, northRate);
        rates.put(Direction.SOUTH, southRate);
        rates.put(Direction.EAST, eastRate);
        rates.put(Direction.WEST, westRate);
        return rates;
    }

    /**
     * Generates a SimulationConfig using this preset's arrival rates and a specified seed.
     *
     * @param seed random seed for reproducible vehicle arrivals
     * @return populated SimulationConfig
     */
    public SimulationConfig toConfig(long seed) {
        return SimulationConfig.builder()
                .arrivalRates(getArrivalRates())
                .seed(seed)
                .build();
    }

    /**
     * Generates a SimulationConfig using this preset with the default seed.
     */
    public SimulationConfig toConfig() {
        return toConfig(SimulationConfig.DEFAULT_SEED);
    }
}
