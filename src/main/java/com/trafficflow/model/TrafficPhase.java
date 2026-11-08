package com.trafficflow.model;

/**
 * Represents the collision-free operational phases of the intersection signal controller.
 * Invariants:
 *  - Conflicting approaches (North/South vs. East/West) NEVER receive GREEN simultaneously.
 *  - Clear transitions are enforced: Green -> Yellow -> All-Red clearance -> Green.
 */
public enum TrafficPhase {
    NS_GREEN("North-South Green", SignalColor.GREEN, SignalColor.RED),
    NS_YELLOW("North-South Yellow (Clearance)", SignalColor.YELLOW, SignalColor.RED),
    ALL_RED("All-Red Clearance", SignalColor.RED, SignalColor.RED),
    EW_GREEN("East-West Green", SignalColor.RED, SignalColor.GREEN),
    EW_YELLOW("East-West Yellow (Clearance)", SignalColor.RED, SignalColor.YELLOW);

    private final String description;
    private final SignalColor nsSignal;
    private final SignalColor ewSignal;

    TrafficPhase(String description, SignalColor nsSignal, SignalColor ewSignal) {
        this.description = description;
        this.nsSignal = nsSignal;
        this.ewSignal = ewSignal;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Resolves the signal color for a specific approach direction under this phase.
     *
     * @param direction approach direction (NORTH, SOUTH, EAST, WEST)
     * @return active SignalColor for that direction
     */
    public SignalColor getSignal(Direction direction) {
        if (direction == null) {
            return SignalColor.RED;
        }
        return switch (direction) {
            case NORTH, SOUTH -> nsSignal;
            case EAST, WEST -> ewSignal;
        };
    }

    public boolean isGreen() {
        return this == NS_GREEN || this == EW_GREEN;
    }

    public boolean isYellow() {
        return this == NS_YELLOW || this == EW_YELLOW;
    }

    public boolean isAllRed() {
        return this == ALL_RED;
    }
}
