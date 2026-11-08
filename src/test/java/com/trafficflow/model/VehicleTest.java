package com.trafficflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VehicleTest {

    @Test
    @DisplayName("Vehicle initialized with correct defaults and properties")
    void testVehicleInitialization() {
        Vehicle v = new Vehicle(1, Direction.NORTH, 10.0, 10.0, 4.5);

        assertEquals(1, v.getId());
        assertEquals(Direction.NORTH, v.getOrigin());
        assertEquals(10.0, v.getSpawnTime());
        assertEquals(10.0, v.getSpeed());
        assertEquals(4.5, v.getLength());
        assertEquals(0.0, v.getPosition());
        assertEquals(0.0, v.getWaitTime());
        assertFalse(v.hasPassedStopLine());
        assertFalse(v.isStopped());
    }

    @Test
    @DisplayName("Moving vehicle advances position and does not accumulate wait time")
    void testMovingVehicleAdvances() {
        Vehicle v = new Vehicle(2, Direction.SOUTH, 0.0, 10.0);
        v.step(1.0); // 1 second at 10.0 m/s

        assertEquals(10.0, v.getPosition(), 0.001);
        assertEquals(0.0, v.getWaitTime(), 0.001);
    }

    @Test
    @DisplayName("Stopped vehicle accumulates wait time and does not advance position")
    void testStoppedVehicleAccumulatesWaitTime() {
        Vehicle v = new Vehicle(3, Direction.EAST, 0.0, 10.0);
        v.setSpeed(0.0);
        assertTrue(v.isStopped());

        v.step(2.5); // Stopped for 2.5 seconds
        assertEquals(0.0, v.getPosition(), 0.001);
        assertEquals(2.5, v.getWaitTime(), 0.001);

        v.step(1.5);
        assertEquals(4.0, v.getWaitTime(), 0.001);
    }

    @Test
    @DisplayName("Invalid vehicle arguments throw exceptions")
    void testInvalidVehicleArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                new Vehicle(4, Direction.WEST, 0.0, -5.0));

        assertThrows(IllegalArgumentException.class, () ->
                new Vehicle(5, Direction.WEST, 0.0, 10.0, -2.0));

        assertThrows(NullPointerException.class, () ->
                new Vehicle(6, null, 0.0, 10.0));
    }
}
