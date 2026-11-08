package com.trafficflow.ui;

import com.trafficflow.model.Direction;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.SimulationSnapshot;
import com.trafficflow.model.TrafficPhase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Main application layout container.
 * Composes:
 *  - Top Header: Clean, professional branding bar with live controller and phase badges.
 *  - Center: The scalable 2D top-down IntersectionView.
 *  - Bottom Panel: Placeholder layout reserved for future simulation controls, scenario selectors, and metrics.
 */
public class MainView extends BorderPane {

    private final IntersectionView intersectionView;
    private final Label phaseBadge;
    private final Circle phaseIndicator;
    private final Label controllerBadge;

    private final Label activeVehiclesLabel;
    private final Label processedVehiclesLabel;
    private final Label totalQueueLabel;

    public MainView() {
        setStyle("-fx-background-color: #121316;");

        // Center: Intersection Visualization
        this.intersectionView = new IntersectionView();
        setCenter(intersectionView);

        // Top: Header Bar
        this.phaseIndicator = new Circle(5.0, Color.web("#34c759"));
        this.phaseBadge = new Label("NORTH-SOUTH GREEN");
        this.controllerBadge = new Label("Adaptive Controller");
        setTop(createHeader());

        // Bottom: Status & Future Controls Panel
        this.activeVehiclesLabel = new Label("0");
        this.processedVehiclesLabel = new Label("0");
        this.totalQueueLabel = new Label("0");
        setBottom(createBottomPanel());
    }

    private HBox createHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 24, 12, 24));
        header.setStyle("-fx-background-color: #17181c; " +
                "-fx-border-color: transparent transparent #262930 transparent; " +
                "-fx-border-width: 1.5;");

        // App Branding
        VBox titleBox = new VBox(2);
        Label title = new Label("TrafficFlow");
        title.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 19px; " +
                "-fx-font-weight: bold; " +
                "-fx-text-fill: #ffffff;");

        Label subtitle = new Label("Adaptive Traffic Signal Optimization & Simulation System");
        subtitle.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 11px; " +
                "-fx-text-fill: #8b92a0;");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Phase Status Pill
        HBox phasePill = new HBox(8);
        phasePill.setAlignment(Pos.CENTER);
        phasePill.setPadding(new Insets(6, 14, 6, 12));
        phasePill.setStyle("-fx-background-color: #20232a; " +
                "-fx-background-radius: 16; " +
                "-fx-border-color: #313540; " +
                "-fx-border-radius: 16; " +
                "-fx-border-width: 1;");

        phaseBadge.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-text-fill: #dcdfe6;");
        phasePill.getChildren().addAll(phaseIndicator, phaseBadge);

        // Controller Status Pill
        HBox controllerPill = new HBox(6);
        controllerPill.setAlignment(Pos.CENTER);
        controllerPill.setPadding(new Insets(6, 14, 6, 14));
        controllerPill.setStyle("-fx-background-color: #1d2636; " +
                "-fx-background-radius: 16; " +
                "-fx-border-color: #2b3a55; " +
                "-fx-border-radius: 16; " +
                "-fx-border-width: 1;");

        controllerBadge.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-text-fill: #61afef;");
        controllerPill.getChildren().add(controllerBadge);

        header.getChildren().addAll(titleBox, spacer, phasePill, controllerPill);
        return header;
    }

    private HBox createBottomPanel() {
        HBox panel = new HBox(20);
        panel.setAlignment(Pos.CENTER_LEFT);
        panel.setPadding(new Insets(12, 24, 12, 24));
        panel.setStyle("-fx-background-color: #17181c; " +
                "-fx-border-color: #262930 transparent transparent transparent; " +
                "-fx-border-width: 1.5;");

        // KPI Status Cards
        HBox kpiContainer = new HBox(12);
        kpiContainer.setAlignment(Pos.CENTER_LEFT);
        kpiContainer.getChildren().addAll(
                createMiniKpi("Total Queue", totalQueueLabel, "#e5c07b"),
                createMiniKpi("Active Cars", activeVehiclesLabel, "#98c379"),
                createMiniKpi("Processed", processedVehiclesLabel, "#61afef")
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Reserved panel indicator for Step 2
        Label placeholderLabel = new Label("Simulation Controls & Telemetry Dashboard [Reserved for Next Steps]");
        placeholderLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 11px; " +
                "-fx-text-fill: #636d80; " +
                "-fx-background-color: #1f2229; " +
                "-fx-background-radius: 6; " +
                "-fx-padding: 6 12 6 12;");

        panel.getChildren().addAll(kpiContainer, spacer, placeholderLabel);
        return panel;
    }

    private HBox createMiniKpi(String title, Label valueLabel, String valueColor) {
        HBox card = new HBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(4, 12, 4, 12));
        card.setStyle("-fx-background-color: #20232a; " +
                "-fx-background-radius: 6; " +
                "-fx-border-color: #2f3440; " +
                "-fx-border-radius: 6; " +
                "-fx-border-width: 1;");

        Label titleLabel = new Label(title + ":");
        titleLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 11px; " +
                "-fx-text-fill: #8b92a0;");

        valueLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 11px; " +
                "-fx-font-weight: bold; " +
                "-fx-text-fill: " + valueColor + ";");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    /**
     * Updates visual components with state from a SimulationSnapshot.
     *
     * @param snapshot immutable simulation snapshot
     */
    public void updateSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        // Update central canvas
        intersectionView.updateSnapshot(snapshot);

        // Update header badges
        TrafficPhase phase = snapshot.currentPhase();
        phaseBadge.setText(phase.name().replace('_', ' '));
        if (phase.isGreen()) {
            phaseIndicator.setFill(Color.web("#34c759"));
        } else if (phase.isYellow()) {
            phaseIndicator.setFill(Color.web("#ffcc00"));
        } else {
            phaseIndicator.setFill(Color.web("#ff3b30"));
        }

        controllerBadge.setText(snapshot.controllerName());

        // Update bottom mini KPIs
        int totalQueue = 0;
        for (int q : snapshot.queueLengths().values()) {
            totalQueue += q;
        }
        totalQueueLabel.setText(String.valueOf(totalQueue));
        activeVehiclesLabel.setText(String.valueOf(snapshot.totalVehiclesActive()));
        processedVehiclesLabel.setText(String.valueOf(snapshot.totalVehiclesProcessed()));
    }

    public IntersectionView getIntersectionView() {
        return intersectionView;
    }
}
