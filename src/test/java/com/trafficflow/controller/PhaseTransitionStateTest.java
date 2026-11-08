package com.trafficflow.controller;

import com.trafficflow.model.Direction;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.TrafficPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PhaseTransitionStateTest {

    private PhaseTransitionState state;

    @BeforeEach
    void setUp() {
        // Yellow: 4s, All-Red: 2s
        state = new PhaseTransitionState(TrafficPhase.NS_GREEN, 4.0, 2.0);
    }

    @Test
    @DisplayName("Initial state should be steady green and not in transition")
    void testInitialState() {
        assertEquals(TrafficPhase.NS_GREEN, state.getCurrentPhase());
        assertFalse(state.isInTransition());
        assertEquals(PhaseTransitionState.Step.NONE, state.getTransitionStep());
        assertEquals(0.0, state.getTimeInCurrentPhase(), 0.001);
    }

    @Test
    @DisplayName("Follows strict sequence: GREEN -> YELLOW -> ALL_RED -> NEXT GREEN")
    void testSafeTransitionSequence() {
        // Steady green for 10 seconds
        state.update(10.0);
        assertEquals(10.0, state.getTimeInCurrentPhase(), 0.001);
        assertEquals(TrafficPhase.NS_GREEN, state.getCurrentPhase());

        // Initiate transition to EW_GREEN
        boolean initiated = state.initiateTransition(TrafficPhase.EW_GREEN);
        assertTrue(initiated);
        assertTrue(state.isInTransition());
        assertEquals(PhaseTransitionState.Step.YELLOW, state.getTransitionStep());
        assertEquals(TrafficPhase.NS_YELLOW, state.getCurrentPhase());

        // Advance 2 seconds into yellow
        state.update(2.0);
        assertEquals(TrafficPhase.NS_YELLOW, state.getCurrentPhase());
        assertEquals(PhaseTransitionState.Step.YELLOW, state.getTransitionStep());

        // Advance remaining 2 seconds of yellow -> should transition to ALL_RED
        state.update(2.0);
        assertEquals(TrafficPhase.ALL_RED, state.getCurrentPhase());
        assertEquals(PhaseTransitionState.Step.ALL_RED, state.getTransitionStep());

        // Advance 2 seconds of all-red -> should transition to EW_GREEN
        state.update(2.0);
        assertEquals(TrafficPhase.EW_GREEN, state.getCurrentPhase());
        assertFalse(state.isInTransition());
        assertEquals(PhaseTransitionState.Step.NONE, state.getTransitionStep());
        assertEquals(0.0, state.getTimeInCurrentPhase(), 0.001);
    }

    @Test
    @DisplayName("Cannot initiate another transition while one is already in progress")
    void testCannotInterruptActiveTransition() {
        state.initiateTransition(TrafficPhase.EW_GREEN);
        assertTrue(state.isInTransition());

        // Attempting to initiate again should return false
        assertFalse(state.initiateTransition(TrafficPhase.NS_GREEN));
        assertFalse(state.initiateTransition(TrafficPhase.EW_GREEN));
    }

    @Test
    @DisplayName("Cannot transition to the same phase or a non-green phase")
    void testTransitionValidation() {
        // Cannot transition to self
        assertFalse(state.initiateTransition(TrafficPhase.NS_GREEN));

        // Cannot transition to non-green phase
        assertThrows(IllegalArgumentException.class, () ->
                state.initiateTransition(TrafficPhase.NS_YELLOW));
        assertThrows(IllegalArgumentException.class, () ->
                state.initiateTransition(TrafficPhase.ALL_RED));
    }

    @Test
    @DisplayName("Exact time carryover across step boundaries")
    void testTimeCarryover() {
        state.initiateTransition(TrafficPhase.EW_GREEN); // Yellow: 4s, All-Red: 2s

        // Advance by 7 seconds in a single step (4s yellow + 2s all-red + 1s green)
        state.update(7.0);

        assertEquals(TrafficPhase.EW_GREEN, state.getCurrentPhase());
        assertFalse(state.isInTransition());
        assertEquals(1.0, state.getTimeInCurrentPhase(), 0.001);
    }

    @Test
    @DisplayName("Signal colors match expected state during transitions")
    void testSignalColorsDuringTransition() {
        state.initiateTransition(TrafficPhase.EW_GREEN);

        // During NS_YELLOW:
        assertEquals(SignalColor.YELLOW, state.getSignal(Direction.NORTH));
        assertEquals(SignalColor.YELLOW, state.getSignal(Direction.SOUTH));
        assertEquals(SignalColor.RED, state.getSignal(Direction.EAST));
        assertEquals(SignalColor.RED, state.getSignal(Direction.WEST));

        // Advance to ALL_RED:
        state.update(4.0);
        for (Direction d : Direction.values()) {
            assertEquals(SignalColor.RED, state.getSignal(d), "All signals must be RED during clearance");
        }

        // Advance to EW_GREEN:
        state.update(2.0);
        assertEquals(SignalColor.RED, state.getSignal(Direction.NORTH));
        assertEquals(SignalColor.RED, state.getSignal(Direction.SOUTH));
        assertEquals(SignalColor.GREEN, state.getSignal(Direction.EAST));
        assertEquals(SignalColor.GREEN, state.getSignal(Direction.WEST));
    }
}
