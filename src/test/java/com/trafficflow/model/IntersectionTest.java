package com.trafficflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IntersectionTest {

    @Test
    @DisplayName("Intersection initializes all four approaches and default all-red phase")
    void testIntersectionInitialization() {
        Intersection intersection = new Intersection();

        for (Direction dir : Direction.values()) {
            assertNotNull(intersection.getApproach(dir));
            assertEquals(dir, intersection.getApproach(dir).getDirection());
            assertEquals(SignalColor.RED, intersection.getSignal(dir));
        }
        assertEquals(TrafficPhase.ALL_RED, intersection.getCurrentPhase());
        assertEquals(0, intersection.getTotalQueueLength());
        assertEquals(0, intersection.getTotalActiveVehicles());
        assertEquals(0, intersection.getTotalClearedVehicles());
    }

    @Test
    @DisplayName("Changing phase immediately alters signal colors across all approaches")
    void testPhaseTransitionReflectedInSignals() {
        Intersection intersection = new Intersection();

        intersection.setPhase(TrafficPhase.NS_GREEN);
        assertEquals(SignalColor.GREEN, intersection.getSignal(Direction.NORTH));
        assertEquals(SignalColor.GREEN, intersection.getSignal(Direction.SOUTH));
        assertEquals(SignalColor.RED, intersection.getSignal(Direction.EAST));
        assertEquals(SignalColor.RED, intersection.getSignal(Direction.WEST));

        intersection.setPhase(TrafficPhase.EW_GREEN);
        assertEquals(SignalColor.RED, intersection.getSignal(Direction.NORTH));
        assertEquals(SignalColor.RED, intersection.getSignal(Direction.SOUTH));
        assertEquals(SignalColor.GREEN, intersection.getSignal(Direction.EAST));
        assertEquals(SignalColor.GREEN, intersection.getSignal(Direction.WEST));
    }

    @Test
    @DisplayName("Total queue length aggregates across all four approaches")
    void testTotalQueueAggregation() {
        Intersection intersection = new Intersection();

        // Add vehicle to North and vehicle to East
        Vehicle vNorth = new Vehicle(1, Direction.NORTH, 0.0, 10.0);
        Vehicle vEast = new Vehicle(2, Direction.EAST, 0.0, 10.0);

        intersection.getApproach(Direction.NORTH).tryAddVehicle(vNorth);
        intersection.getApproach(Direction.EAST).tryAddVehicle(vEast);

        // Keep in ALL_RED for 20 seconds so both reach stop line and stop
        for (int i = 0; i < 20; i++) {
            intersection.update(1.0, i + 1);
        }

        assertEquals(1, intersection.getQueueLength(Direction.NORTH));
        assertEquals(1, intersection.getQueueLength(Direction.EAST));
        assertEquals(0, intersection.getQueueLength(Direction.SOUTH));
        assertEquals(0, intersection.getQueueLength(Direction.WEST));
        assertEquals(2, intersection.getTotalQueueLength());
    }
}
