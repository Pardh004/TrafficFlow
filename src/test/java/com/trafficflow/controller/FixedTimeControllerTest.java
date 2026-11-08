package com.trafficflow.controller;

import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.TrafficPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FixedTimeControllerTest {

    private FixedTimeController controller;
    private Intersection intersection;

    @BeforeEach
    void setUp() {
        // NS Green: 30s, EW Green: 20s, Yellow: 4s, All-Red: 2s
        controller = new FixedTimeController(30.0, 20.0, 4.0, 2.0);
        intersection = new Intersection();
    }

    @Test
    @DisplayName("Fixed-time controller initializes properly with NS_GREEN")
    void testInitialState() {
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        assertFalse(controller.isInTransition());
        assertEquals("Fixed-Time Controller", controller.getName());
    }

    @Test
    @DisplayName("Executes full cyclic schedule: NS_GREEN -> NS_YELLOW -> ALL_RED -> EW_GREEN -> EW_YELLOW -> ALL_RED")
    void testFullCycleExecution() {
        // Step 1: NS Green for 29 seconds (should remain green)
        controller.update(29.0, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        assertEquals(TrafficPhase.NS_GREEN, intersection.getCurrentPhase());
        assertFalse(controller.isInTransition());

        // Step 2: Reach 30 seconds -> transition to NS_YELLOW should trigger
        controller.update(1.0, intersection);
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase());
        assertEquals(TrafficPhase.NS_YELLOW, intersection.getCurrentPhase());
        assertTrue(controller.isInTransition());

        // Step 3: Advance 4 seconds of Yellow -> transition to ALL_RED
        controller.update(4.0, intersection);
        assertEquals(TrafficPhase.ALL_RED, controller.getCurrentPhase());
        assertEquals(TrafficPhase.ALL_RED, intersection.getCurrentPhase());
        assertTrue(controller.isInTransition());

        // Step 4: Advance 2 seconds of All-Red -> transition to EW_GREEN
        controller.update(2.0, intersection);
        assertEquals(TrafficPhase.EW_GREEN, controller.getCurrentPhase());
        assertEquals(TrafficPhase.EW_GREEN, intersection.getCurrentPhase());
        assertFalse(controller.isInTransition());

        // Step 5: EW Green for 20 seconds -> transition to EW_YELLOW
        controller.update(20.0, intersection);
        assertEquals(TrafficPhase.EW_YELLOW, controller.getCurrentPhase());
        assertTrue(controller.isInTransition());

        // Step 6: 4s Yellow -> ALL_RED
        controller.update(4.0, intersection);
        assertEquals(TrafficPhase.ALL_RED, controller.getCurrentPhase());

        // Step 7: 2s All-Red -> NS_GREEN (cycle completed!)
        controller.update(2.0, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        assertFalse(controller.isInTransition());
    }

    @Test
    @DisplayName("Conflicting approaches never receive GREEN simultaneously throughout the cycle")
    void testSignalSafetyInvariantAcrossCycle() {
        // Run continuous simulation for 5 full cycles (5 * (30+4+2+20+4+2) = 310 seconds)
        for (double t = 0; t < 310; t += 0.5) {
            controller.update(0.5, intersection);

            SignalColor northSignal = controller.getSignal(Direction.NORTH);
            SignalColor southSignal = controller.getSignal(Direction.SOUTH);
            SignalColor eastSignal = controller.getSignal(Direction.EAST);
            SignalColor westSignal = controller.getSignal(Direction.WEST);

            boolean nsCanProceed = (northSignal == SignalColor.GREEN || northSignal == SignalColor.YELLOW);
            boolean ewCanProceed = (eastSignal == SignalColor.GREEN || eastSignal == SignalColor.YELLOW);

            assertFalse(nsCanProceed && ewCanProceed,
                    "Safety violation at t=" + t + ": conflicting approaches allowed to proceed!");
            assertEquals(northSignal, southSignal, "Opposing North and South signals must match");
            assertEquals(eastSignal, westSignal, "Opposing East and West signals must match");
        }
    }

    @Test
    @DisplayName("Reset returns controller to initial NS_GREEN state")
    void testReset() {
        // Advance into EW phase
        controller.update(40.0, intersection);
        assertEquals(TrafficPhase.EW_GREEN, controller.getCurrentPhase());

        controller.reset();
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        assertFalse(controller.isInTransition());
        assertEquals(0.0, controller.getTimeInCurrentPhase(), 0.001);
    }

    @Test
    @DisplayName("Invalid parameters throw IllegalArgumentException")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new FixedTimeController(-10.0, 20.0, 4.0, 2.0));
        assertThrows(IllegalArgumentException.class, () ->
                new FixedTimeController(30.0, -20.0, 4.0, 2.0));
        assertThrows(IllegalArgumentException.class, () ->
                new FixedTimeController(30.0, 20.0, 0.0, 2.0));
    }
}
