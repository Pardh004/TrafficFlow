package com.trafficflow.controller;

import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.TrafficPhase;

/**
 * Common contract for traffic signal scheduling strategies.
 * Provides a uniform interface for both fixed-time cyclic controllers
 * and dynamic adaptive controllers.
 * Completely decoupled from JavaFX and the presentation layer.
 */
public interface SignalController {

    /**
     * Steps the controller forward in time, evaluates traffic conditions,
     * updates signal phase timers, and synchronizes the intersection state.
     *
     * @param deltaSeconds elapsed simulation time in seconds
     * @param intersection the intersection model to monitor and control
     */
    void update(double deltaSeconds, Intersection intersection);

    /**
     * Returns the currently active traffic phase.
     *
     * @return current TrafficPhase
     */
    TrafficPhase getCurrentPhase();

    /**
     * Retrieves the signal color currently displayed for a specific approach.
     *
     * @param direction approach direction
     * @return active SignalColor
     */
    SignalColor getSignal(Direction direction);

    /**
     * Returns the elapsed time in seconds that the current phase has been continuously active.
     *
     * @return duration in seconds
     */
    double getTimeInCurrentPhase();

    /**
     * Indicates whether the controller is currently executing a clearance transition
     * (i.e. displaying YELLOW or ALL_RED).
     *
     * @return true if transitioning between greens, false if in steady green
     */
    boolean isInTransition();

    /**
     * Resets the controller to its initial state.
     */
    void reset();

    /**
     * Returns a human-readable name of the controller strategy.
     *
     * @return controller strategy name
     */
    String getName();
}
