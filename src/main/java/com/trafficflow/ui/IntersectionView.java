package com.trafficflow.ui;

import com.trafficflow.model.Direction;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.SimulationSnapshot;
import com.trafficflow.model.TrafficPhase;
import com.trafficflow.model.VehicleSnapshot;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.EnumMap;
import java.util.Map;

/**
 * High-performance, scalable 2D top-down visualization of the four-way intersection.
 *
 * Renders:
 *  - Distinguishable asphalt road surfaces and sidewalk curbs.
 *  - Right-hand drive lanes with yellow medians, white dashed lines, and stop lines.
 *  - Crosswalk zebra stripes and directional lane guidance arrows.
 *  - Prominent traffic signal heads at each approach reflecting current phase states.
 *  - Directional markers (NORTH, SOUTH, EAST, WEST).
 *
 * Automatically resizes and scales proportionally when the window changes dimensions.
 */
public class IntersectionView extends Pane {

    private final Canvas canvas;
    private final Map<Direction, SignalColor> currentSignals = new EnumMap<>(Direction.class);
    private TrafficPhase currentPhase = TrafficPhase.ALL_RED;
    private Map<Direction, java.util.List<VehicleSnapshot>> currentVehicles = new EnumMap<>(Direction.class);

    // Palette
    private static final Color BG_TERRAIN = Color.web("#14161a");
    private static final Color SIDEWALK_CURB = Color.web("#22252c");
    private static final Color SIDEWALK_BORDER = Color.web("#353a45");
    private static final Color ASPHALT = Color.web("#2c2f37");
    private static final Color INTERSECTION_BOX = Color.web("#31353f");
    private static final Color YELLOW_LINE = Color.web("#f1c40f");
    private static final Color WHITE_LINE = Color.web("#e6e8eb");
    private static final Color LABEL_COLOR = Color.web("#9aa0ac");

    public IntersectionView() {
        this.canvas = new Canvas();
        getChildren().add(canvas);

        // Bind canvas dimensions to parent pane for automatic responsive resizing
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        widthProperty().addListener((obs, oldVal, newVal) -> draw());
        heightProperty().addListener((obs, oldVal, newVal) -> draw());

        // Default initial signals
        for (Direction dir : Direction.values()) {
            currentSignals.put(dir, SignalColor.RED);
        }
    }

    /**
     * Updates signal heads and intersection state from a SimulationSnapshot.
     *
     * @param snapshot current immutable simulation snapshot
     */
    public void updateSnapshot(SimulationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        this.currentPhase = snapshot.currentPhase();
        this.currentSignals.putAll(snapshot.signals());
        this.currentVehicles = snapshot.activeVehicles();
        draw();
    }

    /**
     * Directly updates signal states for testing or standalone display.
     */
    public void updateSignals(Map<Direction, SignalColor> signals, TrafficPhase phase) {
        if (signals != null) {
            this.currentSignals.putAll(signals);
        }
        if (phase != null) {
            this.currentPhase = phase;
        }
        draw();
    }

    private void draw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 10 || h <= 10) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // Fill background terrain
        gc.setFill(BG_TERRAIN);
        gc.fillRect(0, 0, w, h);

        double cx = w / 2.0;
        double cy = h / 2.0;

        // Dynamic scale proportional to window size
        double scale = Math.min(w, h) / 700.0;
        scale = Math.max(0.65, Math.min(scale, 2.5));

        double roadWidth = 150.0 * scale; // Two lanes per road (75px each)
        double halfRoad = roadWidth / 2.0;
        double laneWidth = halfRoad;

        // 1. Draw Sidewalk Curb Corners
        drawSidewalks(gc, w, h, cx, cy, halfRoad, scale);

        // 2. Draw Asphalt Roads
        gc.setFill(ASPHALT);
        // Vertical road (North-South)
        gc.fillRect(cx - halfRoad, 0, roadWidth, h);
        // Horizontal road (East-West)
        gc.fillRect(0, cy - halfRoad, w, roadWidth);

        // Intersection conflict box
        gc.setFill(INTERSECTION_BOX);
        gc.fillRect(cx - halfRoad, cy - halfRoad, roadWidth, roadWidth);

        // Box border outline
        gc.setStroke(Color.web("#3d424e"));
        gc.setLineWidth(1.5 * scale);
        gc.strokeRect(cx - halfRoad, cy - halfRoad, roadWidth, roadWidth);

        // 3. Draw Lane Markings, Medians, and Crosswalks
        drawMarkings(gc, w, h, cx, cy, halfRoad, scale);

        // 4. Draw live vehicles above the road markings
        drawVehicles(gc, w, h, cx, cy, halfRoad, scale);

        // 5. Draw Direction Labels
        drawDirectionLabels(gc, w, h, cx, cy, halfRoad, scale);

        // 6. Draw Traffic Signal Heads
        drawSignalHeads(gc, cx, cy, halfRoad, scale);
    }


    /**
     * Draws vehicles using only immutable snapshots supplied by the simulation engine.
     * Model positions represent the vehicle front measured from the approach entrance.
     */
    private void drawVehicles(GraphicsContext gc, double w, double h,
                              double cx, double cy, double halfRoad, double scale) {
        if (currentVehicles == null || currentVehicles.isEmpty()) {
            return;
        }

        double crosswalkWidth = 24.0 * scale;
        double stopLineGap = 4.0 * scale;
        double northStopY = cy - halfRoad - crosswalkWidth - stopLineGap;
        double southStopY = cy + halfRoad + crosswalkWidth + stopLineGap;
        double westStopX = cx - halfRoad - crosswalkWidth - stopLineGap;
        double eastStopX = cx + halfRoad + crosswalkWidth + stopLineGap;

        // The model's standard stop line is at 150 m. Scale the approach distance
        // to the available canvas space while keeping vehicle dimensions readable.
        double northPpm = Math.max(0.35, northStopY / 150.0);
        double southPpm = Math.max(0.35, (h - southStopY) / 150.0);
        double westPpm = Math.max(0.35, westStopX / 150.0);
        double eastPpm = Math.max(0.35, (w - eastStopX) / 150.0);

        Color[] vehicleColors = {
                Color.web("#61afef"), Color.web("#e5c07b"), Color.web("#98c379"),
                Color.web("#c678dd"), Color.web("#e06c75"), Color.web("#56b6c2")
        };

        for (Direction direction : Direction.values()) {
            java.util.List<VehicleSnapshot> vehicles = currentVehicles.get(direction);
            if (vehicles == null) {
                continue;
            }
            for (VehicleSnapshot vehicle : vehicles) {
                Color body = vehicleColors[Math.floorMod(vehicle.id(), vehicleColors.length)];
                double visualLength = Math.max(20.0, 24.0 * scale);
                double visualWidth = Math.max(11.0, 15.0 * scale);
                double x;
                double y;
                double rotation;

                switch (direction) {
                    case NORTH -> { // North approach travels down-screen.
                        double frontY = northStopY - (150.0 - vehicle.position()) * northPpm;
                        x = cx - halfRoad * 0.5;
                        y = frontY - visualLength / 2.0;
                        rotation = 90.0;
                    }
                    case SOUTH -> { // South approach travels up-screen.
                        double frontY = southStopY + (150.0 - vehicle.position()) * southPpm;
                        x = cx + halfRoad * 0.5;
                        y = frontY + visualLength / 2.0;
                        rotation = 270.0;
                    }
                    case WEST -> { // West approach travels right-screen.
                        double frontX = westStopX - (150.0 - vehicle.position()) * westPpm;
                        x = frontX - visualLength / 2.0;
                        y = cy + halfRoad * 0.5;
                        rotation = 0.0;
                    }
                    case EAST -> { // East approach travels left-screen.
                        double frontX = eastStopX + (150.0 - vehicle.position()) * eastPpm;
                        x = frontX + visualLength / 2.0;
                        y = cy - halfRoad * 0.5;
                        rotation = 180.0;
                    }
                    default -> throw new IllegalStateException("Unexpected direction: " + direction);
                }

                drawVehicle(gc, x, y, visualLength, visualWidth, body, rotation, scale);
            }
        }
    }

    private void drawVehicle(GraphicsContext gc, double x, double y,
                             double length, double width, Color body,
                             double rotation, double scale) {
        gc.save();
        gc.translate(x, y);
        gc.rotate(rotation);

        double left = -length / 2.0;
        double top = -width / 2.0;

        // Soft shadow, body, windshield and rear window.
        gc.setFill(Color.color(0, 0, 0, 0.38));
        gc.fillRoundRect(left + 1.5 * scale, top + 2 * scale, length, width,
                6 * scale, 6 * scale);
        gc.setFill(body);
        gc.fillRoundRect(left, top, length, width, 6 * scale, 6 * scale);
        gc.setStroke(Color.web("#101216"));
        gc.setLineWidth(1.0 * scale);
        gc.strokeRoundRect(left, top, length, width, 6 * scale, 6 * scale);

        gc.setFill(Color.web("#17212b", 0.88));
        gc.fillRoundRect(left + length * 0.34, top + width * 0.13,
                length * 0.34, width * 0.74, 3 * scale, 3 * scale);
        gc.setFill(Color.web("#dce6f2", 0.75));
        gc.fillRoundRect(left + length * 0.76, top + width * 0.16,
                length * 0.10, width * 0.22, 2 * scale, 2 * scale);

        // Headlights make the direction of travel easier to identify.
        gc.setFill(Color.web("#fff2c2"));
        gc.fillRoundRect(length * 0.40, top + width * 0.08,
                length * 0.12, Math.max(2, width * 0.16), 1, 1);
        gc.fillRoundRect(length * 0.40, top + width * 0.76,
                length * 0.12, Math.max(2, width * 0.16), 1, 1);
        gc.restore();
    }

    private void drawSidewalks(GraphicsContext gc, double w, double h, double cx, double cy, double hr, double scale) {
        gc.setFill(SIDEWALK_CURB);
        gc.setStroke(SIDEWALK_BORDER);
        gc.setLineWidth(2.0 * scale);

        double cornerRadius = 16.0 * scale;

        // Top-Left corner
        gc.fillRoundRect(0, 0, cx - hr, cy - hr, cornerRadius, cornerRadius);
        gc.strokeRoundRect(0, 0, cx - hr, cy - hr, cornerRadius, cornerRadius);

        // Top-Right corner
        gc.fillRoundRect(cx + hr, 0, w - (cx + hr), cy - hr, cornerRadius, cornerRadius);
        gc.strokeRoundRect(cx + hr, 0, w - (cx + hr), cy - hr, cornerRadius, cornerRadius);

        // Bottom-Left corner
        gc.fillRoundRect(0, cy + hr, cx - hr, h - (cy + hr), cornerRadius, cornerRadius);
        gc.strokeRoundRect(0, cy + hr, cx - hr, h - (cy + hr), cornerRadius, cornerRadius);

        // Bottom-Right corner
        gc.fillRoundRect(cx + hr, cy + hr, w - (cx + hr), h - (cy + hr), cornerRadius, cornerRadius);
        gc.strokeRoundRect(cx + hr, cy + hr, w - (cx + hr), h - (cy + hr), cornerRadius, cornerRadius);
    }

    private void drawMarkings(GraphicsContext gc, double w, double h, double cx, double cy, double hr, double scale) {
        double crosswalkWidth = 24.0 * scale;
        double stopLineGap = 4.0 * scale;

        // --- Double Yellow Medians (Center Lines) ---
        gc.setStroke(YELLOW_LINE);
        gc.setLineWidth(2.0 * scale);
        double yellowOffset = 2.0 * scale;

        // North median
        gc.strokeLine(cx - yellowOffset, 0, cx - yellowOffset, cy - hr - crosswalkWidth - stopLineGap);
        gc.strokeLine(cx + yellowOffset, 0, cx + yellowOffset, cy - hr - crosswalkWidth - stopLineGap);

        // South median
        gc.strokeLine(cx - yellowOffset, cy + hr + crosswalkWidth + stopLineGap, cx - yellowOffset, h);
        gc.strokeLine(cx + yellowOffset, cy + hr + crosswalkWidth + stopLineGap, cx + yellowOffset, h);

        // West median
        gc.strokeLine(0, cy - yellowOffset, cx - hr - crosswalkWidth - stopLineGap, cy - yellowOffset);
        gc.strokeLine(0, cy + yellowOffset, cx - hr - crosswalkWidth - stopLineGap, cy + yellowOffset);

        // East median
        gc.strokeLine(cx + hr + crosswalkWidth + stopLineGap, cy - yellowOffset, w, cy - yellowOffset);
        gc.strokeLine(cx + hr + crosswalkWidth + stopLineGap, cy + yellowOffset, w, cy + yellowOffset);

        // --- Stop Lines (Solid White, Inbound Lanes) ---
        gc.setStroke(WHITE_LINE);
        gc.setLineWidth(4.0 * scale);

        // North inbound (cars moving Southbound, on right side of vertical road: x in [cx - hr, cx])
        double northStopY = cy - hr - crosswalkWidth - stopLineGap;
        gc.strokeLine(cx - hr, northStopY, cx, northStopY);

        // South inbound (cars moving Northbound, on right side of vertical road: x in [cx, cx + hr])
        double southStopY = cy + hr + crosswalkWidth + stopLineGap;
        gc.strokeLine(cx, southStopY, cx + hr, southStopY);

        // West inbound (cars moving Eastbound, on right side of horizontal road: y in [cy, cy + hr])
        double westStopX = cx - hr - crosswalkWidth - stopLineGap;
        gc.strokeLine(westStopX, cy, westStopX, cy + hr);

        // East inbound (cars moving Westbound, on right side of horizontal road: y in [cy - hr, cy])
        double eastStopX = cx + hr + crosswalkWidth + stopLineGap;
        gc.strokeLine(eastStopX, cy - hr, eastStopX, cy);

        // --- Crosswalk Zebra Stripes ---
        drawCrosswalk(gc, cx - hr, cy - hr - crosswalkWidth, hr * 2, crosswalkWidth, true, scale);
        drawCrosswalk(gc, cx - hr, cy + hr, hr * 2, crosswalkWidth, true, scale);
        drawCrosswalk(gc, cx - hr - crosswalkWidth, cy - hr, crosswalkWidth, hr * 2, false, scale);
        drawCrosswalk(gc, cx + hr, cy - hr, crosswalkWidth, hr * 2, false, scale);

        // --- Lane Flow Guidance Arrows ---
        drawLaneArrow(gc, cx - hr * 0.5, northStopY - 40 * scale, "↓", scale);
        drawLaneArrow(gc, cx + hr * 0.5, southStopY + 40 * scale, "↑", scale);
        drawLaneArrow(gc, westStopX - 40 * scale, cy + hr * 0.5, "→", scale);
        drawLaneArrow(gc, eastStopX + 40 * scale, cy - hr * 0.5, "←", scale);
    }

    private void drawCrosswalk(GraphicsContext gc, double x, double y, double width, double height, boolean isHorizontal, double scale) {
        gc.setFill(Color.web("#e6e8eb", 0.65));
        double stripeSize = 6.0 * scale;
        double gap = 6.0 * scale;

        if (isHorizontal) {
            for (double curX = x + 3 * scale; curX < x + width - 3 * scale; curX += (stripeSize + gap)) {
                gc.fillRect(curX, y + 2 * scale, Math.min(stripeSize, x + width - curX), height - 4 * scale);
            }
        } else {
            for (double curY = y + 3 * scale; curY < y + height - 3 * scale; curY += (stripeSize + gap)) {
                gc.fillRect(x + 2 * scale, curY, width - 4 * scale, Math.min(stripeSize, y + height - curY));
            }
        }
    }

    private void drawLaneArrow(GraphicsContext gc, double x, double y, String arrow, double scale) {
        gc.setFill(Color.web("#8b909a", 0.75));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18 * scale));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(arrow, x, y + 6 * scale);
    }

    private void drawDirectionLabels(GraphicsContext gc, double w, double h, double cx, double cy, double hr, double scale) {
        gc.setFill(LABEL_COLOR);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12 * scale));
        gc.setTextAlign(TextAlignment.CENTER);

        // North
        gc.fillText("NORTH APPROACH", cx, Math.max(22 * scale, cy - hr - 80 * scale));
        // South
        gc.fillText("SOUTH APPROACH", cx, Math.min(h - 12 * scale, cy + hr + 90 * scale));

        // West
        gc.fillText("WEST APPROACH", Math.max(65 * scale, cx - hr - 95 * scale), cy - 10 * scale);
        // East
        gc.fillText("EAST APPROACH", Math.min(w - 65 * scale, cx + hr + 95 * scale), cy - 10 * scale);
    }

    private void drawSignalHeads(GraphicsContext gc, double cx, double cy, double hr, double scale) {
        double offset = 38.0 * scale;

        // North Approach Signal (mounted on west curb near stop line)
        drawSignalHead(gc, cx - hr - 28 * scale, cy - hr - offset, currentSignals.get(Direction.NORTH), "NORTH", scale);

        // South Approach Signal (mounted on east curb near stop line)
        drawSignalHead(gc, cx + hr + 8 * scale, cy + hr + offset - 20 * scale, currentSignals.get(Direction.SOUTH), "SOUTH", scale);

        // West Approach Signal (mounted on south curb near stop line)
        drawSignalHead(gc, cx - hr - offset - 10 * scale, cy + hr + 8 * scale, currentSignals.get(Direction.WEST), "WEST", scale);

        // East Approach Signal (mounted on north curb near stop line)
        drawSignalHead(gc, cx + hr + offset - 15 * scale, cy - hr - 32 * scale, currentSignals.get(Direction.EAST), "EAST", scale);
    }

    private void drawSignalHead(GraphicsContext gc, double x, double y, SignalColor color, String label, double scale) {
        if (color == null) color = SignalColor.RED;

        double housingWidth = 20.0 * scale;
        double housingHeight = 50.0 * scale;
        double lampRadius = 4.5 * scale;
        double lampCenter = x + housingWidth / 2.0;

        // Outer housing
        gc.setFill(Color.web("#121316"));
        gc.fillRoundRect(x, y, housingWidth, housingHeight, 6 * scale, 6 * scale);
        gc.setStroke(Color.web("#353a45"));
        gc.setLineWidth(1.5 * scale);
        gc.strokeRoundRect(x, y, housingWidth, housingHeight, 6 * scale, 6 * scale);

        // Lamps: Red (top), Yellow (mid), Green (bottom)
        double rY = y + 10 * scale;
        double yY = y + 25 * scale;
        double gY = y + 40 * scale;

        // Red lamp
        boolean isRed = (color == SignalColor.RED);
        gc.setFill(isRed ? Color.web("#ff3b30") : Color.web("#300f0f"));
        gc.fillOval(lampCenter - lampRadius, rY - lampRadius, lampRadius * 2, lampRadius * 2);

        // Yellow lamp
        boolean isYellow = (color == SignalColor.YELLOW);
        gc.setFill(isYellow ? Color.web("#ffcc00") : Color.web("#302700"));
        gc.fillOval(lampCenter - lampRadius, yY - lampRadius, lampRadius * 2, lampRadius * 2);

        // Green lamp
        boolean isGreen = (color == SignalColor.GREEN);
        gc.setFill(isGreen ? Color.web("#34c759") : Color.web("#07220e"));
        gc.fillOval(lampCenter - lampRadius, gY - lampRadius, lampRadius * 2, lampRadius * 2);

        // Outline glow on active lamp
        gc.setLineWidth(1.0 * scale);
        if (isRed) {
            gc.setStroke(Color.web("#ff6961"));
            gc.strokeOval(lampCenter - lampRadius, rY - lampRadius, lampRadius * 2, lampRadius * 2);
        } else if (isYellow) {
            gc.setStroke(Color.web("#ffe066"));
            gc.strokeOval(lampCenter - lampRadius, yY - lampRadius, lampRadius * 2, lampRadius * 2);
        } else if (isGreen) {
            gc.setStroke(Color.web("#7ee787"));
            gc.strokeOval(lampCenter - lampRadius, gY - lampRadius, lampRadius * 2, lampRadius * 2);
        }
    }
}
