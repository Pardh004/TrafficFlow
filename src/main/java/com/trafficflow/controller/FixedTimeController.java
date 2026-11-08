package com.trafficflow.controller;

import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.TrafficPhase;

import java.util.Objects;

/**
 * Traditional fixed-time traffic signal controller.
 * Operates on predetermined, cyclic time allocations regardless of fluctuating traffic volumes.
 * Cycles continuously through:
 *   NS_GREEN (T_ns) → NS_YELLOW (T_yellow) → ALL_RED (T_allred) →
 *   EW_GREEN (T_ew) → EW_YELLOW (T_yellow) → ALL_RED (T_allred) → NS_GREEN ...
 */
public class FixedTimeController implements SignalController {

    private final double nsGreenDuration;
    private final double ewGreenDuration;
    private final PhaseTransitionState transitionState;

    /**
     * Constructs a FixedTimeController with custom interval durations.
     *
     * @param nsGreenDuration duration of North-South green in seconds
     * @param ewGreenDuration duration of East-West green in seconds
     * @param yellowDuration  duration of yellow clearance in seconds
     * @param allRedDuration  duration of all-red clearance in seconds
     */
    public FixedTimeController(double nsGreenDuration,
                               double ewGreenDuration,
                               double yellowDuration,
                               double allRedDuration) {
        if (nsGreenDuration <= 0) {
            throw new IllegalArgumentException("North-South green duration must be positive");
        }
        if (ewGreenDuration <= 0) {
            throw new IllegalArgumentException("East-West green duration must be positive");
        }
        this.nsGreenDuration = nsGreenDuration;
        this.ewGreenDuration = ewGreenDuration;
        this.transitionState = new PhaseTransitionState(TrafficPhase.NS_GREEN, yellowDuration, allRedDuration);
    }

    /**
     * Convenience constructor with standard default timings:
     * 30s NS Green, 30s EW Green, 4s Yellow, 2s All-Red.
     */
    public FixedTimeController() {
        this(30.0, 30.0, 4.0, 2.0);
    }

    @Override
    public void update(double deltaSeconds, Intersection intersection) {
        Objects.requireNonNull(intersection, "Intersection cannot be null");
        if (deltaSeconds <= 0) {
            return;
        }

        double remaining = deltaSeconds;

        while (remaining > 0) {
            if (transitionState.isInTransition()) {
                // If in transition, transitionState.update advances through yellow and all-red,
                // and seamlessly enters the new green phase if time remains.
                transitionState.update(remaining);
                remaining = 0.0;
            } else {
                TrafficPhase current = transitionState.getCurrentPhase();
                double maxGreen = (current == TrafficPhase.NS_GREEN) ? nsGreenDuration : ewGreenDuration;
                double timeInGreen = transitionState.getTimeInCurrentPhase();
                double timeToExpiration = Math.max(0.0, maxGreen - timeInGreen);

                if (timeToExpiration <= 0.0001) {
                    TrafficPhase target = (current == TrafficPhase.NS_GREEN) ? TrafficPhase.EW_GREEN : TrafficPhase.NS_GREEN;
                    transitionState.initiateTransition(target);
                } else if (remaining >= timeToExpiration) {
                    transitionState.update(timeToExpiration);
                    remaining -= timeToExpiration;
                    TrafficPhase target = (current == TrafficPhase.NS_GREEN) ? TrafficPhase.EW_GREEN : TrafficPhase.NS_GREEN;
                    transitionState.initiateTransition(target);
                } else {
                    transitionState.update(remaining);
                    remaining = 0.0;
                }
            }
        }

        // Synchronize intersection with active phase
        intersection.setPhase(transitionState.getCurrentPhase());
    }

    @Override
    public TrafficPhase getCurrentPhase() {
        return transitionState.getCurrentPhase();
    }

    @Override
    public SignalColor getSignal(Direction direction) {
        return transitionState.getSignal(direction);
    }

    @Override
    public double getTimeInCurrentPhase() {
        return transitionState.getTimeInCurrentPhase();
    }

    @Override
    public boolean isInTransition() {
        return transitionState.isInTransition();
    }

    @Override
    public void reset() {
        transitionState.reset(TrafficPhase.NS_GREEN);
    }

    @Override
    public String getName() {
        return "Fixed-Time Controller";
    }

    public double getNsGreenDuration() {
        return nsGreenDuration;
    }

    public double getEwGreenDuration() {
        return ewGreenDuration;
    }

    public double getYellowDuration() {
        return transitionState.getYellowDuration();
    }

    public double getAllRedDuration() {
        return transitionState.getAllRedDuration();
    }
}
