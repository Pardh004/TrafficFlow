package com.trafficflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TrafficPhaseTest {

    @Test
    @DisplayName("North-South Green must never grant green to East or West")
    void testNorthSouthGreenSafetyInvariant() {
        TrafficPhase phase = TrafficPhase.NS_GREEN;

        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.NORTH));
        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.SOUTH));

        // Conflicting directions must strictly be RED
        assertEquals(SignalColor.RED, phase.getSignal(Direction.EAST));
        assertEquals(SignalColor.RED, phase.getSignal(Direction.WEST));
    }

    @Test
    @DisplayName("East-West Green must never grant green to North or South")
    void testEastWestGreenSafetyInvariant() {
        TrafficPhase phase = TrafficPhase.EW_GREEN;

        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.EAST));
        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.WEST));

        // Conflicting directions must strictly be RED
        assertEquals(SignalColor.RED, phase.getSignal(Direction.NORTH));
        assertEquals(SignalColor.RED, phase.getSignal(Direction.SOUTH));
    }

    @Test
    @DisplayName("All-Red clearance phase must display RED to all approaches")
    void testAllRedClearance() {
        TrafficPhase phase = TrafficPhase.ALL_RED;

        for (Direction dir : Direction.values()) {
            assertEquals(SignalColor.RED, phase.getSignal(dir),
                    "Direction " + dir + " must be RED during ALL_RED clearance");
        }
        assertTrue(phase.isAllRed());
        assertFalse(phase.isGreen());
        assertFalse(phase.isYellow());
    }

    @Test
    @DisplayName("No phase may ever allow perpendicular approaches to both have non-red signals")
    void testGlobalConflictAvoidanceInvariant() {
        for (TrafficPhase phase : TrafficPhase.values()) {
            SignalColor northSignal = phase.getSignal(Direction.NORTH);
            SignalColor southSignal = phase.getSignal(Direction.SOUTH);
            SignalColor eastSignal = phase.getSignal(Direction.EAST);
            SignalColor westSignal = phase.getSignal(Direction.WEST);

            boolean nsCanProceed = northSignal == SignalColor.GREEN || northSignal == SignalColor.YELLOW;
            boolean ewCanProceed = eastSignal == SignalColor.GREEN || eastSignal == SignalColor.YELLOW;

            // Invariant: Both corridors must never be active at the same time
            assertFalse(nsCanProceed && ewCanProceed,
                    "CRITICAL SAFETY VIOLATION: Phase " + phase + " allows conflicting approaches to proceed!");

            // Invariant: Paired opposing approaches must match
            assertEquals(northSignal, southSignal, "North and South signals must match");
            assertEquals(eastSignal, westSignal, "East and West signals must match");
        }
    }
}
