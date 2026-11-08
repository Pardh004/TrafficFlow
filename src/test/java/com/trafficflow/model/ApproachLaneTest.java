package com.trafficflow.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ApproachLaneTest {

    private ApproachLane lane;

    @BeforeEach
    void setUp() {
        // Road length 200m, stop line at 150m, safe gap 2.5m, cruising speed 10.0 m/s
        lane = new ApproachLane(Direction.NORTH, 200.0, 150.0, 2.5, 10.0);
    }

    @Test
    @DisplayName("Lane admits new vehicle when entrance is clear")
    void testVehicleSpawning() {
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0);
        assertTrue(lane.canSpawnVehicle());
        assertTrue(lane.tryAddVehicle(v1));
        assertEquals(1, lane.getVehicles().size());

        // Immediately trying to add a second vehicle at the same position (0.0) should be blocked by safe gap
        Vehicle v2 = new Vehicle(2, Direction.NORTH, 0.0, 10.0);
        assertFalse(lane.canSpawnVehicle());
        assertFalse(lane.tryAddVehicle(v2));

        // Advance lane by 2 seconds (v1 travels 20 meters, well past the 4.5m + 2.5m buffer)
        lane.update(2.0, SignalColor.GREEN, 2.0);
        assertTrue(lane.canSpawnVehicle());
        assertTrue(lane.tryAddVehicle(v2));
        assertEquals(2, lane.getVehicles().size());
    }

    @Test
    @DisplayName("Vehicles stop at stop line on RED and form a queue")
    void testRedSignalStopLineQueueing() {
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0); // 4.5m length
        lane.tryAddVehicle(v1);

        // Advance 20 seconds under RED signal
        // Distance to stop line is 150m, at 10m/s it should arrive in 15 seconds and wait for 5 seconds
        for (int i = 0; i < 20; i++) {
            lane.update(1.0, SignalColor.RED, i + 1);
        }

        assertEquals(150.0, v1.getPosition(), 0.001, "Vehicle must stop exactly at stop line");
        assertTrue(v1.isStopped(), "Vehicle must be stopped at RED light");
        assertFalse(v1.hasPassedStopLine());
        assertEquals(1, lane.getQueueLength(), "Queue length should be 1");
        assertEquals(5.0, lane.getMaxWaitTime(), 0.01, "Vehicle should have waited for ~5s");
    }

    @Test
    @DisplayName("Trailing vehicle maintains safe buffer spacing behind stopped leading vehicle")
    void testSafeFollowingDistanceBuffer() {
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0, 4.5);
        lane.tryAddVehicle(v1);

        // Drive v1 up near the stop line
        lane.update(15.0, SignalColor.RED, 15.0);
        assertEquals(150.0, v1.getPosition(), 0.001);

        // Spawn trailing vehicle v2
        Vehicle v2 = new Vehicle(2, Direction.NORTH, 15.0, 10.0, 4.5);
        assertTrue(lane.tryAddVehicle(v2));

        // Let v2 drive up behind v1
        for (int i = 0; i < 20; i++) {
            lane.update(1.0, SignalColor.RED, 15.0 + i + 1);
        }

        // v1 front = 150.0. v1 rear bumper = 150.0 - 4.5 = 145.5.
        // v2 front must stop at rear bumper - safeGap (2.5) = 145.5 - 2.5 = 143.0.
        assertEquals(143.0, v2.getPosition(), 0.001, "Trailing vehicle must maintain safe bumper gap");
        assertTrue(v2.isStopped());
        assertEquals(2, lane.getQueueLength(), "Both vehicles must be counted in queue");
    }

    @Test
    @DisplayName("Vehicles cross intersection on GREEN and clear the system")
    void testGreenSignalClearance() {
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0, 4.5);
        lane.tryAddVehicle(v1);

        // Hold at red for 15 seconds until it reaches stop line
        lane.update(15.0, SignalColor.RED, 15.0);
        assertEquals(150.0, v1.getPosition(), 0.001);

        // Light turns GREEN: advance 10 seconds (v1 travels from 150m to 250m; laneLength is 200m)
        lane.update(10.0, SignalColor.GREEN, 25.0);

        assertTrue(v1.hasPassedStopLine());
        assertEquals(0, lane.getVehicles().size(), "Vehicle should be cleared from active list");
        assertEquals(1, lane.getClearedVehicleCount(), "Vehicle should be registered as cleared");
        assertEquals(0, lane.getQueueLength(), "Queue should now be empty");
        assertNotNull(v1.getDepartureTime());
    }
}
