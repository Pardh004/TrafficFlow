package com.trafficflow.ui;

import com.trafficflow.model.Direction;
import com.trafficflow.model.SignalColor;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.Objects;

/**
 * Reusable JavaFX traffic signal component representing a standard 3-lamp signal head (RED, YELLOW, GREEN).
 * Provides visual indication with active lamp glow and realistic dimmed state for inactive lamps.
 */
public class SignalView extends VBox {

    private static final double LAMP_RADIUS = 7.0;

    // Active illuminated colors
    private static final Color RED_ON = Color.web("#ff3b30");
    private static final Color YELLOW_ON = Color.web("#ffcc00");
    private static final Color GREEN_ON = Color.web("#34c759");

    // Dimmed inactive colors
    private static final Color RED_OFF = Color.web("#331010");
    private static final Color YELLOW_OFF = Color.web("#332b00");
    private static final Color GREEN_OFF = Color.web("#0a2810");

    // Subtle dark outlines
    private static final Color HOUSING_BG = Color.web("#141518");
    private static final Color HOUSING_BORDER = Color.web("#2c3038");

    private final Direction direction;
    private final Circle redLamp;
    private final Circle yellowLamp;
    private final Circle greenLamp;
    private SignalColor currentColor;

    public SignalView(Direction direction) {
        this.direction = Objects.requireNonNull(direction, "Direction cannot be null");
        this.currentColor = SignalColor.RED;

        setAlignment(Pos.CENTER);
        setSpacing(4);
        setStyle("-fx-background-color: #141518; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: #2c3038; " +
                "-fx-border-radius: 8; " +
                "-fx-border-width: 1.5; " +
                "-fx-padding: 6 5 6 5;");

        // Direction header badge
        Label dirLabel = new Label(direction.name().substring(0, 1));
        dirLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; " +
                "-fx-font-size: 9px; " +
                "-fx-font-weight: bold; " +
                "-fx-text-fill: #9aa0ac;");

        redLamp = new Circle(LAMP_RADIUS);
        yellowLamp = new Circle(LAMP_RADIUS);
        greenLamp = new Circle(LAMP_RADIUS);

        getChildren().addAll(dirLabel, redLamp, yellowLamp, greenLamp);
        updateSignal(SignalColor.RED);
    }

    /**
     * Updates the illuminated state of the signal lamps to match the specified SignalColor.
     *
     * @param color active SignalColor (RED, YELLOW, GREEN)
     */
    public void updateSignal(SignalColor color) {
        if (color == null) {
            color = SignalColor.RED;
        }
        this.currentColor = color;

        // Reset lamps to dimmed
        redLamp.setFill(RED_OFF);
        redLamp.setStroke(Color.web("#1e0a0a"));
        redLamp.setEffect(null);

        yellowLamp.setFill(YELLOW_OFF);
        yellowLamp.setStroke(Color.web("#1e1a00"));
        yellowLamp.setEffect(null);

        greenLamp.setFill(GREEN_OFF);
        greenLamp.setStroke(Color.web("#051408"));
        greenLamp.setEffect(null);

        // Light up the active lamp
        switch (color) {
            case RED -> {
                redLamp.setFill(RED_ON);
                redLamp.setStroke(Color.web("#ff7b72"));
                redLamp.setEffect(new DropShadow(8, RED_ON));
            }
            case YELLOW -> {
                yellowLamp.setFill(YELLOW_ON);
                yellowLamp.setStroke(Color.web("#ffe066"));
                yellowLamp.setEffect(new DropShadow(8, YELLOW_ON));
            }
            case GREEN -> {
                greenLamp.setFill(GREEN_ON);
                greenLamp.setStroke(Color.web("#7ee787"));
                greenLamp.setEffect(new DropShadow(8, GREEN_ON));
            }
        }
    }

    public Direction getDirection() {
        return direction;
    }

    public SignalColor getCurrentColor() {
        return currentColor;
    }
}
