package com.trafficflow.controller;

import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.TrafficPhase;
import com.trafficflow.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AdaptiveControllerTest {

    private AdaptiveController controller;
    private Intersection intersection;

    @BeforeEach
    void setUp() {
        // Min Green: 10s, Max Green: 60s, Yellow: 4s, All-Red: 2s,
        // Fairness Threshold: 30s, Queue Weight: 1.0, Wait Weight: 0.5, Hysteresis: 5.0
        controller = new AdaptiveController(10.0, 60.0, 4.0, 2.0, 30.0, 1.0, 0.5, 5.0);
        intersection = new Intersection();
    }

    private int vehicleIdCounter = 1;

    private void addStoppedVehicle(Direction direction, double waitTime, double distanceAhead) {
        // 4.5m vehicle placed before stop line (150m)
        double stopLine = intersection.getApproach(direction).getStopLinePosition();
        Vehicle v = new Vehicle(vehicleIdCounter++, direction, 0.0, 0.0, 4.5);
        v.setPosition(stopLine - distanceAhead);
        v.setSpeed(0.0);
        // Step wait time directly
        v.step(waitTime);
        intersection.getApproach(direction).insertVehicle(v);
    }

    @Test
    @DisplayName("Guarantees Minimum Green Duration even under overwhelming opposing demand")
    void testMinimumGreenDurationEnforced() {
        // Active: NS_GREEN
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());

        // Create massive opposing demand on EAST: 10 queued cars, 40s wait time each
        for (int i = 0; i < 10; i++) {
            addStoppedVehicle(Direction.EAST, 40.0, i * 8.0);
        }
        assertTrue(intersection.getQueueLength(Direction.EAST) >= 1);

        // Advance 9 seconds (less than minGreenDuration = 10s)
        for (int i = 0; i < 9; i++) {
            controller.update(1.0, intersection);
            assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(),
                    "Signal must remain green until minimum green time (10s) is reached");
            assertFalse(controller.isInTransition());
        }

        // Advance to 10th second: now min green is satisfied, switch should trigger
        controller.update(1.0, intersection);
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase(),
                    "At 10s, overwhelming opposing demand should trigger transition to yellow");
        assertTrue(controller.isInTransition());
    }

    @Test
    @DisplayName("Guarantees Maximum Green Duration to prevent starvation when opposing cars are waiting")
    void testMaximumGreenDurationEnforced() {
        // Active: NS corridor with heavy ongoing queue (8 vehicles)
        for (int i = 0; i < 8; i++) {
            addStoppedVehicle(Direction.NORTH, 5.0, i * 8.0);
        }

        // Opposing: small queue on EAST (2 vehicles, small wait time so fairness doesn't trigger early)
        addStoppedVehicle(Direction.EAST, 2.0, 0.0);
        addStoppedVehicle(Direction.EAST, 2.0, 8.0);

        // Advance up to 59 seconds: NS should hold green because active score > opposing score
        for (int i = 0; i < 59; i++) {
            controller.update(1.0, intersection);
            assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        }

        // At 60 seconds (maxGreenDuration): forced switch must occur
        controller.update(1.0, intersection);
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase(),
                "At maxGreenDuration (60s), switch to yellow must be forced despite heavy active traffic");
        assertTrue(controller.isInTransition());
    }

    @Test
    @DisplayName("Extends green beyond max green if opposing corridor has zero demand")
    void testNoOpposingDemandExtendsGreenIndefinitely() {
        // North has 2 vehicles
        addStoppedVehicle(Direction.NORTH, 5.0, 0.0);
        addStoppedVehicle(Direction.NORTH, 5.0, 8.0);

        // East and West have ZERO vehicles
        assertEquals(0, intersection.getQueueLength(Direction.EAST));
        assertEquals(0, intersection.getQueueLength(Direction.WEST));

        // Advance well beyond maxGreenDuration (75 seconds)
        for (int i = 0; i < 75; i++) {
            controller.update(1.0, intersection);
            assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(),
                    "Should extend green past 60s because nobody is waiting on opposing corridor");
            assertFalse(controller.isInTransition());
        }
    }

    @Test
    @DisplayName("Fairness safeguard triggers switch for long-waiting vehicle on low-volume road")
    void testFairnessThresholdTriggersSwitch() {
        // Active corridor (North) has 10 vehicles
        for (int i = 0; i < 10; i++) {
            addStoppedVehicle(Direction.NORTH, 5.0, i * 8.0);
        }

        // Opposing corridor (East) has only 1 vehicle, but it has waited for 32 seconds (>= fairnessThreshold 30s)
        addStoppedVehicle(Direction.EAST, 32.0, 0.0);

        // At t < 10s: min green holds
        for (int i = 0; i < 9; i++) {
            controller.update(1.0, intersection);
            assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        }

        // At t = 10s: min green satisfied, fairness threshold forces switch despite North having 10 vs 1
        controller.update(1.0, intersection);
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase(),
                "Fairness threshold must force transition as soon as minimum green is met");
    }

    @Test
    @DisplayName("Gap-out switches immediately when active queue clears and opposing vehicles wait")
    void testGapOutWhenActiveQueueCleared() {
        // Active corridor (NS) has 0 queued vehicles
        assertEquals(0, intersection.getQueueLength(Direction.NORTH));
        assertEquals(0, intersection.getQueueLength(Direction.SOUTH));

        // Opposing corridor (East) has 2 queued vehicles
        addStoppedVehicle(Direction.EAST, 5.0, 0.0);
        addStoppedVehicle(Direction.EAST, 5.0, 8.0);

        // Advance through min green (10s)
        for (int i = 0; i < 9; i++) {
            controller.update(1.0, intersection);
            assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        }

        // At t=10s: gap-out triggers switch immediately
        controller.update(1.0, intersection);
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase(),
                "Gap-out must switch immediately when active queue is 0 and opposing has cars");
    }

    @Test
    @DisplayName("Demand score requires hysteresis to switch when both sides have traffic")
    void testHysteresisRequirement() {
        // Active (NS): 4 cars, wait = 2s -> Score = (1.0 * 4) + (0.5 * 2) = 5.0
        for (int i = 0; i < 4; i++) {
            addStoppedVehicle(Direction.NORTH, 2.0, i * 8.0);
        }

        // Opposing (EW): 5 cars, wait = 4s -> Score = (1.0 * 5) + (0.5 * 4) = 7.0
        // Score difference is 7.0 - 5.0 = 2.0 (less than hysteresis 5.0!)
        for (int i = 0; i < 5; i++) {
            addStoppedVehicle(Direction.EAST, 4.0, i * 8.0);
        }

        // Advance to 15 seconds (past min green, but before max green)
        for (int i = 0; i < 15; i++) {
            controller.update(1.0, intersection);
            assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(),
                    "Score diff (2.0) is below hysteresis (5.0), so active corridor green must be extended");
        }

        // Now increase opposing EW wait time to 16s -> Opposing Score = 5 + (0.5 * 16) = 13.0
        // Score difference is 13.0 - 5.0 = 8.0 (exceeds hysteresis 5.0!)
        intersection.getApproach(Direction.EAST).getVehicles().get(0).step(12.0);

        controller.update(1.0, intersection);
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase(),
                "Opposing score now exceeds active score by hysteresis buffer, triggering switch");
    }

    @Test
    @DisplayName("Safe transition sequence is strictly observed: GREEN -> YELLOW -> ALL_RED -> NEXT GREEN")
    void testSafeTransitionRouting() {
        // Force a switch at t=10s via empty queue
        addStoppedVehicle(Direction.EAST, 10.0, 0.0);

        for (int i = 0; i < 10; i++) {
            controller.update(1.0, intersection);
        }
        // At t=10s, switches to NS_YELLOW
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase());
        assertTrue(controller.isInTransition());

        // Yellow interval is 4 seconds
        controller.update(2.0, intersection);
        assertEquals(TrafficPhase.NS_YELLOW, controller.getCurrentPhase());
        controller.update(2.0, intersection);
        assertEquals(TrafficPhase.ALL_RED, controller.getCurrentPhase());

        // All-red interval is 2 seconds
        controller.update(1.0, intersection);
        assertEquals(TrafficPhase.ALL_RED, controller.getCurrentPhase());
        controller.update(1.0, intersection);

        // Now in EW_GREEN
        assertEquals(TrafficPhase.EW_GREEN, controller.getCurrentPhase());
        assertFalse(controller.isInTransition());
    }

    @Test
    @DisplayName("Reset returns adaptive controller to initial state")
    void testReset() {
        addStoppedVehicle(Direction.EAST, 40.0, 0.0);
        controller.update(15.0, intersection);
        assertTrue(controller.isInTransition() || controller.getCurrentPhase() == TrafficPhase.EW_GREEN);

        controller.reset();
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        assertFalse(controller.isInTransition());
        assertEquals(0.0, controller.getTimeInCurrentPhase(), 0.001);
    }

    @Test
    @DisplayName("Rejects non-positive, NaN, and infinite delta times without state mutation")
    void testDeltaTimeValidation() {
        // Initialize with 5 seconds of normal green
        controller.update(5.0, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
        assertEquals(5.0, controller.getTimeInCurrentPhase(), 0.001);
        assertFalse(controller.isInTransition());

        // Add opposing demand that would otherwise trigger a transition if evaluated
        addStoppedVehicle(Direction.EAST, 40.0, 0.0);

        // 1. Zero delta time: must be rejected with no state change
        controller.update(0.0, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(), "Phase must not change on zero delta");
        assertEquals(5.0, controller.getTimeInCurrentPhase(), 0.001, "Timer must not advance on zero delta");
        assertFalse(controller.isInTransition(), "Transition must not initiate on zero delta");

        // 2. Negative delta time: must be rejected with no state change
        controller.update(-1.0, intersection);
        controller.update(-50.0, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(), "Phase must not change on negative delta");
        assertEquals(5.0, controller.getTimeInCurrentPhase(), 0.001, "Timer must not advance on negative delta");
        assertFalse(controller.isInTransition(), "Transition must not initiate on negative delta");

        // 3. NaN delta time: must be rejected with no state change
        controller.update(Double.NaN, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(), "Phase must not change on NaN delta");
        assertEquals(5.0, controller.getTimeInCurrentPhase(), 0.001, "Timer must not advance on NaN delta");
        assertFalse(controller.isInTransition(), "Transition must not initiate on NaN delta");

        // 4. Positive infinity delta time: must be rejected with no state change
        controller.update(Double.POSITIVE_INFINITY, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(), "Phase must not change on +Infinity delta");
        assertEquals(5.0, controller.getTimeInCurrentPhase(), 0.001, "Timer must not advance on +Infinity delta");
        assertFalse(controller.isInTransition(), "Transition must not initiate on +Infinity delta");

        // Also verify Negative infinity
        controller.update(Double.NEGATIVE_INFINITY, intersection);
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase(), "Phase must not change on -Infinity delta");
        assertEquals(5.0, controller.getTimeInCurrentPhase(), 0.001, "Timer must not advance on -Infinity delta");
        assertFalse(controller.isInTransition(), "Transition must not initiate on -Infinity delta");

        // 5. Normal positive delta time: must proceed and advance properly
        controller.update(1.0, intersection);
        assertEquals(6.0, controller.getTimeInCurrentPhase(), 0.001, "Timer must advance on valid positive delta");
        assertEquals(TrafficPhase.NS_GREEN, controller.getCurrentPhase());
    }
}
