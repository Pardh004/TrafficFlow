package com.trafficflow.model;

/**
 * Represents the four geographic approaches to the intersection.
 */
public enum Direction {
    NORTH,
    SOUTH,
    EAST,
    WEST;

    /**
     * Returns the directly opposite direction.
     * North opposes South, East opposes West.
     *
     * @return opposite Direction
     */
    public Direction getOpposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }

    /**
     * Determines if another approach is a conflicting cross-street approach.
     * North/South conflict with East/West, and vice-versa.
     *
     * @param other the direction to check against
     * @return true if other is perpendicular (conflicting), false otherwise
     */
    public boolean isConflicting(Direction other) {
        if (other == null) {
            return false;
        }
        return (this == NORTH || this == SOUTH) ? (other == EAST || other == WEST)
                                                : (other == NORTH || other == SOUTH);
    }
}
