package com.trafficflow;

import com.trafficflow.ui.TrafficFlowApplication;

/**
 * Standard non-Application wrapper entry point for launching the TrafficFlow JavaFX application.
 * Bypasses JavaFX runtime launcher checks when executed without module-path flags.
 */
public final class TrafficFlowApp {

    private TrafficFlowApp() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        TrafficFlowApplication.main(args);
    }
}
