package com.trafficflow.controller;

import com.trafficflow.model.Direction;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.TrafficPhase;

import java.util.Objects;

/**
 * Manages signal phase transitions, ensuring strict enforcement of safety intervals:
 *   GREEN → YELLOW → ALL_RED → NEXT GREEN
 *
 * Invariant guarantees:
 *  - Conflicting green phases are separated by mandatory YELLOW and ALL_RED intervals.
 *  - No transition can be interrupted while in progress.
 *  - Time step accumulation is mathematically exact across interval boundaries.
 */
public class PhaseTransitionState {

    public enum Step {
        NONE,       // Steady state (GREEN)
        YELLOW,     // Active approach is displaying YELLOW
        ALL_RED     // All approaches are displaying RED (clearance)
    }

    private final double yellowDuration;
    private final double allRedDuration;

    private TrafficPhase currentPhase;
    private TrafficPhase targetGreenPhase;
    private Step transitionStep;
    private double transitionTimer;
    private double timeInCurrentPhase;

    /**
     * Constructs a PhaseTransitionState starting in a specified green phase.
     *
     * @param initialPhase   starting green phase (e.g. TrafficPhase.NS_GREEN)
     * @param yellowDuration duration of yellow clearance in seconds
     * @param allRedDuration duration of all-red clearance in seconds
     */
    public PhaseTransitionState(TrafficPhase initialPhase, double yellowDuration, double allRedDuration) {
        if (yellowDuration <= 0) {
            throw new IllegalArgumentException("Yellow duration must be strictly positive");
        }
        if (allRedDuration < 0) {
            throw new IllegalArgumentException("All-red duration cannot be negative");
        }
        this.yellowDuration = yellowDuration;
        this.allRedDuration = allRedDuration;
        this.currentPhase = Objects.requireNonNull(initialPhase, "Initial phase cannot be null");
        this.targetGreenPhase = null;
        this.transitionStep = Step.NONE;
        this.transitionTimer = 0.0;
        this.timeInCurrentPhase = 0.0;
    }

    /**
     * Advances the transition timers and phase state machine by deltaSeconds.
     * Handles interval completions seamlessly with exact carry-over.
     *
     * @param deltaSeconds elapsed simulation time in seconds
     */
    public void update(double deltaSeconds) {
        if (deltaSeconds <= 0) {
            return;
        }

        double remaining = deltaSeconds;

        while (remaining > 0) {
            if (transitionStep == Step.YELLOW) {
                double timeNeeded = yellowDuration - transitionTimer;
                if (remaining >= timeNeeded) {
                    transitionTimer = 0.0;
                    timeInCurrentPhase = 0.0;
                    currentPhase = TrafficPhase.ALL_RED;
                    transitionStep = Step.ALL_RED;
                    remaining -= timeNeeded;
                } else {
                    transitionTimer += remaining;
                    timeInCurrentPhase += remaining;
                    remaining = 0.0;
                }
            } else if (transitionStep == Step.ALL_RED) {
                double timeNeeded = allRedDuration - transitionTimer;
                if (remaining >= timeNeeded) {
                    transitionTimer = 0.0;
                    timeInCurrentPhase = 0.0;
                    currentPhase = targetGreenPhase;
                    targetGreenPhase = null;
                    transitionStep = Step.NONE;
                    remaining -= timeNeeded;
                } else {
                    transitionTimer += remaining;
                    timeInCurrentPhase += remaining;
                    remaining = 0.0;
                }
            } else {
                // Steady green phase
                timeInCurrentPhase += remaining;
                remaining = 0.0;
            }
        }
    }

    /**
     * Initiates a safe transition from the current green phase to a target green phase.
     * Enforces GREEN → YELLOW → ALL_RED → NEXT GREEN.
     *
     * @param nextGreenPhase target green phase to switch to
     * @return true if transition was initiated, false if rejected (e.g. already transitioning or identical phase)
     */
    public boolean initiateTransition(TrafficPhase nextGreenPhase) {
        Objects.requireNonNull(nextGreenPhase, "Target green phase cannot be null");

        if (!nextGreenPhase.isGreen()) {
            throw new IllegalArgumentException("Target phase must be a green phase, but was: " + nextGreenPhase);
        }
        if (isInTransition()) {
            return false; // Transition already underway
        }
        if (currentPhase == nextGreenPhase) {
            return false; // Already in target phase
        }

        // Determine appropriate yellow phase based on active corridor
        if (currentPhase == TrafficPhase.NS_GREEN) {
            currentPhase = TrafficPhase.NS_YELLOW;
        } else if (currentPhase == TrafficPhase.EW_GREEN) {
            currentPhase = TrafficPhase.EW_YELLOW;
        } else {
            return false;
        }

        this.targetGreenPhase = nextGreenPhase;
        this.transitionStep = Step.YELLOW;
        this.transitionTimer = 0.0;
        this.timeInCurrentPhase = 0.0;
        return true;
    }

    /**
     * Resets to a specified initial phase and clears active transitions.
     */
    public void reset(TrafficPhase initialPhase) {
        this.currentPhase = Objects.requireNonNull(initialPhase, "Initial phase cannot be null");
        this.targetGreenPhase = null;
        this.transitionStep = Step.NONE;
        this.transitionTimer = 0.0;
        this.timeInCurrentPhase = 0.0;
    }

    public boolean isInTransition() {
        return transitionStep != Step.NONE;
    }

    public Step getTransitionStep() {
        return transitionStep;
    }

    public TrafficPhase getCurrentPhase() {
        return currentPhase;
    }

    public SignalColor getSignal(Direction direction) {
        return currentPhase.getSignal(direction);
    }

    public double getTimeInCurrentPhase() {
        return timeInCurrentPhase;
    }

    public double getYellowDuration() {
        return yellowDuration;
    }

    public double getAllRedDuration() {
        return allRedDuration;
    }

    public TrafficPhase getTargetGreenPhase() {
        return targetGreenPhase;
    }
}
