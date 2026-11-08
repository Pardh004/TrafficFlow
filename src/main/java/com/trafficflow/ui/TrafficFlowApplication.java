package com.trafficflow.ui;

import com.trafficflow.config.ScenarioPreset;
import com.trafficflow.config.SimulationConfig;
import com.trafficflow.controller.AdaptiveController;
import com.trafficflow.engine.SimulationEngine;
import com.trafficflow.model.SimulationSnapshot;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main JavaFX application entry point for TrafficFlow.
 * Initializes the top-level window, establishes the default simulation engine,
 * and renders the initial static intersection visualization.
 */
public class TrafficFlowApplication extends Application {

    private static final double DEFAULT_WIDTH = 1024;
    private static final double DEFAULT_HEIGHT = 768;
    private static final double MIN_WIDTH = 750;
    private static final double MIN_HEIGHT = 600;

    @Override
    public void start(Stage primaryStage) {
        // Instantiate the initial domain simulation configuration and engine
        SimulationConfig config = ScenarioPreset.BALANCED_TRAFFIC.toConfig();
        AdaptiveController controller = new AdaptiveController();
        SimulationEngine engine = new SimulationEngine(config, controller);

        // Capture initial static snapshot
        SimulationSnapshot initialSnapshot = engine.createSnapshot();

        // Create main layout view and bind snapshot
        MainView mainView = new MainView();
        mainView.updateSnapshot(initialSnapshot);

        // Configure Scene and Stage
        Scene scene = new Scene(mainView, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        primaryStage.setTitle("TrafficFlow – Adaptive Traffic Signal Optimization and Simulation System");
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
