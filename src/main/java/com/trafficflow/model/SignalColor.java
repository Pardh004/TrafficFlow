package com.trafficflow.model;

/**
 * Represents the standard states of a traffic signal lamp.
 */
public enum SignalColor {
    RED,
    YELLOW,
    GREEN;

    /**
     * Indicates whether traffic is allowed to enter the intersection.
     *
     * @return true if GREEN, false otherwise
     */
    public boolean canProceed() {
        return this == GREEN;
    }

    /**
     * Indicates whether approaching traffic must prepare to stop.
     *
     * @return true if YELLOW, false otherwise
     */
    public boolean isCaution() {
        return this == YELLOW;
    }

    /**
     * Indicates whether traffic must stop before the stop line.
     *
     * @return true if RED, false otherwise
     */
    public boolean mustStop() {
        return this == RED;
    }
}
