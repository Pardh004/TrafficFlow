package com.trafficflow.controller;

import com.trafficflow.model.Direction;
import com.trafficflow.model.Intersection;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.TrafficPhase;

import java.util.Objects;

/**
 * Intelligent, rule-based adaptive traffic signal controller.
 *
 * Dynamically adjusts green times and phase allocations based on real-time
 * approach queue lengths and vehicle waiting times.
 *
 * Key algorithmic safeguards:
 *  1. Minimum Green Duration (T_min): Prevents rapid phase flickering.
 *  2. Maximum Green Duration (T_max): Prevents heavy corridors from starving opposing traffic.
 *  3. Fairness / Starvation Safeguard: Guarantees green allocation when any vehicle's wait time exceeds a fairness threshold.
 *  4. Gap-Out Cutoff: Terminates green early once the active queue is cleared and opposing vehicles are waiting.
 *  5. Demand Scoring with Hysteresis: Compares weighted demand (queue count + max wait time) with hysteresis to prevent thrashing.
 *  6. Safe Transitions: Always routes switches through YELLOW and ALL_RED clearance intervals.
 */
public class AdaptiveController implements SignalController {

    private final double minGreenDuration;
    private final double maxGreenDuration;
    private final double fairnessThreshold;
    private final double queueWeight;
    private final double waitWeight;
    private final double hysteresis;

    private final PhaseTransitionState transitionState;
    private String lastDecisionReason = "Initial state";

    /**
     * Constructs an AdaptiveController with customized parameters.
     *
     * @param minGreenDuration  minimum allowable green time in seconds (e.g. 10s)
     * @param maxGreenDuration  maximum allowable green time before forced switch in seconds (e.g. 60s)
     * @param yellowDuration    duration of yellow clearance in seconds (e.g. 4s)
     * @param allRedDuration    duration of all-red clearance in seconds (e.g. 2s)
     * @param fairnessThreshold waiting time threshold (in seconds) that forces a fairness override (e.g. 30s)
     * @param queueWeight       importance weight per queued vehicle (e.g. 1.0)
     * @param waitWeight        importance weight per second of max waiting time (e.g. 0.5)
     * @param hysteresis        score buffer required to initiate a switch (e.g. 5.0)
     */
    public AdaptiveController(double minGreenDuration,
                              double maxGreenDuration,
                              double yellowDuration,
                              double allRedDuration,
                              double fairnessThreshold,
                              double queueWeight,
                              double waitWeight,
                              double hysteresis) {
        if (minGreenDuration <= 0) {
            throw new IllegalArgumentException("Minimum green duration must be positive");
        }
        if (maxGreenDuration < minGreenDuration) {
            throw new IllegalArgumentException("Maximum green duration cannot be less than minimum green duration");
        }
        if (fairnessThreshold <= 0) {
            throw new IllegalArgumentException("Fairness threshold must be positive");
        }
        if (queueWeight < 0 || waitWeight < 0) {
            throw new IllegalArgumentException("Weights cannot be negative");
        }
        if (hysteresis < 0) {
            throw new IllegalArgumentException("Hysteresis cannot be negative");
        }

        this.minGreenDuration = minGreenDuration;
        this.maxGreenDuration = maxGreenDuration;
        this.fairnessThreshold = fairnessThreshold;
        this.queueWeight = queueWeight;
        this.waitWeight = waitWeight;
        this.hysteresis = hysteresis;
        this.transitionState = new PhaseTransitionState(TrafficPhase.NS_GREEN, yellowDuration, allRedDuration);
    }

    /**
     * Convenience constructor with proven default configuration:
     * Min Green: 10s, Max Green: 60s, Yellow: 4s, All-Red: 2s,
     * Fairness Threshold: 30s, Queue Weight: 1.0, Wait Weight: 0.5, Hysteresis: 5.0.
     */
    public AdaptiveController() {
        this(10.0, 60.0, 4.0, 2.0, 30.0, 1.0, 0.5, 5.0);
    }

    @Override
    public void update(double deltaSeconds, Intersection intersection) {
        Objects.requireNonNull(intersection, "Intersection cannot be null");
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
            return;
        }

        // Advance transition timers
        transitionState.update(deltaSeconds);

        // If currently in a clearance transition (YELLOW or ALL_RED), do not evaluate decisions
        if (transitionState.isInTransition()) {
            intersection.setPhase(transitionState.getCurrentPhase());
            return;
        }

        TrafficPhase current = transitionState.getCurrentPhase();
        double timeInGreen = transitionState.getTimeInCurrentPhase();

        boolean isNorthSouth = (current == TrafficPhase.NS_GREEN);
        TrafficPhase targetPhase = isNorthSouth ? TrafficPhase.EW_GREEN : TrafficPhase.NS_GREEN;

        // Gather metrics for active and opposing corridors
        int activeQueue = isNorthSouth ? getNsQueue(intersection) : getEwQueue(intersection);
        double activeWait = isNorthSouth ? getNsMaxWait(intersection) : getEwMaxWait(intersection);

        int opposingQueue = isNorthSouth ? getEwQueue(intersection) : getNsQueue(intersection);
        double opposingWait = isNorthSouth ? getEwMaxWait(intersection) : getNsMaxWait(intersection);

        // Guard 1: Minimum Green Duration must be respected
        if (timeInGreen < minGreenDuration) {
            lastDecisionReason = "Holding: Minimum green duration not reached ("
                    + String.format("%.1f", timeInGreen) + "s / " + minGreenDuration + "s)";
            intersection.setPhase(transitionState.getCurrentPhase());
            return;
        }

        // Guard 2: If opposing corridor has zero demand, maintain green even beyond max green
        if (opposingQueue == 0 && opposingWait <= 0.001) {
            lastDecisionReason = "Extending: Opposing corridor has no waiting vehicles";
            intersection.setPhase(transitionState.getCurrentPhase());
            return;
        }

        // Rule 3: Maximum Green Duration exceeded while opposing traffic is waiting
        if (timeInGreen >= maxGreenDuration) {
            lastDecisionReason = "Switching: Maximum green limit reached ("
                    + String.format("%.1f", timeInGreen) + "s >= " + maxGreenDuration + "s)";
            transitionState.initiateTransition(targetPhase);
            intersection.setPhase(transitionState.getCurrentPhase());
            return;
        }

        // Rule 4: Fairness & Anti-Starvation safeguard
        // If any vehicle on the opposing approach has waited past the fairness threshold
        if (opposingWait >= fairnessThreshold) {
            lastDecisionReason = "Switching: Fairness threshold triggered (opposing wait "
                    + String.format("%.1f", opposingWait) + "s >= " + fairnessThreshold + "s)";
            transitionState.initiateTransition(targetPhase);
            intersection.setPhase(transitionState.getCurrentPhase());
            return;
        }

        // Rule 5: Active corridor queue depleted (Gap-out / Early Cut-off)
        // If active approach has 0 queued vehicles and opposing approach has waiting vehicles
        if (activeQueue == 0 && opposingQueue > 0) {
            lastDecisionReason = "Switching: Active corridor queue cleared, opposing queue waiting ("
                    + opposingQueue + " vehicles)";
            transitionState.initiateTransition(targetPhase);
            intersection.setPhase(transitionState.getCurrentPhase());
            return;
        }

        // Rule 6: Weighted Demand Comparison with Hysteresis
        double activeScore = calculateScore(activeQueue, activeWait);
        double opposingScore = calculateScore(opposingQueue, opposingWait);

        if (opposingScore > activeScore + hysteresis) {
            lastDecisionReason = "Switching: Opposing demand score ("
                    + String.format("%.1f", opposingScore) + ") exceeds active score ("
                    + String.format("%.1f", activeScore) + ") by hysteresis";
            transitionState.initiateTransition(targetPhase);
        } else {
            lastDecisionReason = "Extending: Active corridor demand sufficient ("
                    + String.format("%.1f", activeScore) + " vs " + String.format("%.1f", opposingScore) + ")";
        }

        intersection.setPhase(transitionState.getCurrentPhase());
    }

    /**
     * Calculates the composite demand score for a corridor.
     * Score = (queueWeight * queueCount) + (waitWeight * maxWaitSeconds)
     *
     * @param queueCount  number of stopped vehicles
     * @param maxWaitSecs maximum time stopped in seconds
     * @return composite demand score
     */
    public double calculateScore(int queueCount, double maxWaitSecs) {
        return (queueWeight * queueCount) + (waitWeight * maxWaitSecs);
    }

    /**
     * Calculates the real-time demand score for North-South corridor.
     */
    public double getNorthSouthDemand(Intersection intersection) {
        return calculateScore(getNsQueue(intersection), getNsMaxWait(intersection));
    }

    /**
     * Calculates the real-time demand score for East-West corridor.
     */
    public double getEastWestDemand(Intersection intersection) {
        return calculateScore(getEwQueue(intersection), getEwMaxWait(intersection));
    }

    private int getNsQueue(Intersection intersection) {
        return intersection.getQueueLength(Direction.NORTH) + intersection.getQueueLength(Direction.SOUTH);
    }

    private double getNsMaxWait(Intersection intersection) {
        return Math.max(intersection.getMaxWaitTime(Direction.NORTH), intersection.getMaxWaitTime(Direction.SOUTH));
    }

    private int getEwQueue(Intersection intersection) {
        return intersection.getQueueLength(Direction.EAST) + intersection.getQueueLength(Direction.WEST);
    }

    private double getEwMaxWait(Intersection intersection) {
        return Math.max(intersection.getMaxWaitTime(Direction.EAST), intersection.getMaxWaitTime(Direction.WEST));
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
        lastDecisionReason = "Reset to initial state";
    }

    @Override
    public String getName() {
        return "Adaptive Controller";
    }

    public double getMinGreenDuration() {
        return minGreenDuration;
    }

    public double getMaxGreenDuration() {
        return maxGreenDuration;
    }

    public double getFairnessThreshold() {
        return fairnessThreshold;
    }

    public double getQueueWeight() {
        return queueWeight;
    }

    public double getWaitWeight() {
        return waitWeight;
    }

    public double getHysteresis() {
        return hysteresis;
    }

    public double getYellowDuration() {
        return transitionState.getYellowDuration();
    }

    public double getAllRedDuration() {
        return transitionState.getAllRedDuration();
    }

    public String getLastDecisionReason() {
        return lastDecisionReason;
    }
}
