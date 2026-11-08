package com.trafficflow.model;

/**
 * Standalone test runner that verifies core domain models, safety invariants,
 * stop-line behavior, queue logic, and anti-collision spacing directly on any standard JDK.
 */
public class ModelVerificationRunner {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  TrafficFlow - Phase 1 Model Verification Suite  ");
        System.out.println("==================================================");

        runTest("Safety Invariant: NS Green holds EW to Red", ModelVerificationRunner::testNorthSouthGreen);
        runTest("Safety Invariant: EW Green holds NS to Red", ModelVerificationRunner::testEastWestGreen);
        runTest("Safety Invariant: All-Red sets all approaches to Red", ModelVerificationRunner::testAllRedClearance);
        runTest("Safety Invariant: No simultaneous conflicting greens across all phases", ModelVerificationRunner::testGlobalConflictAvoidance);
        runTest("Vehicle: Moving vehicle advances position without accumulating wait time", ModelVerificationRunner::testVehicleMovement);
        runTest("Vehicle: Stopped vehicle accumulates wait time", ModelVerificationRunner::testVehicleWaitTime);
        runTest("ApproachLane: Spawning accepts vehicles when clear and rejects when blocked", ModelVerificationRunner::testSpawningBuffer);
        runTest("ApproachLane: Vehicle stops at stop line on Red and enters queue", ModelVerificationRunner::testRedLightStopLineQueue);
        runTest("ApproachLane: Trailing vehicle maintains safe gap behind stopped leader", ModelVerificationRunner::testSafeFollowingDistance);
        runTest("ApproachLane: Vehicle proceeds across intersection on Green and clears", ModelVerificationRunner::testGreenClearance);
        runTest("Intersection: Multi-approach aggregation and queue counting", ModelVerificationRunner::testIntersectionAggregate);

        System.out.println("--------------------------------------------------");
        System.out.println("Summary: " + testsPassed + " passed, " + testsFailed + " failed.");
        System.out.println("==================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String testName, Runnable test) {
        try {
            test.run();
            System.out.println("[PASS] " + testName);
            testsPassed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] " + testName + " -> " + t.getMessage());
            t.printStackTrace(System.err);
            testsFailed++;
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
    }

    private static void assertEquals(double expected, double actual, double epsilon, String message) {
        if (Math.abs(expected - actual) > epsilon) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual + " (diff > " + epsilon + ")");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    // --- Tests ---

    private static void testNorthSouthGreen() {
        TrafficPhase phase = TrafficPhase.NS_GREEN;
        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.NORTH), "North must be GREEN");
        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.SOUTH), "South must be GREEN");
        assertEquals(SignalColor.RED, phase.getSignal(Direction.EAST), "East must be RED");
        assertEquals(SignalColor.RED, phase.getSignal(Direction.WEST), "West must be RED");
    }

    private static void testEastWestGreen() {
        TrafficPhase phase = TrafficPhase.EW_GREEN;
        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.EAST), "East must be GREEN");
        assertEquals(SignalColor.GREEN, phase.getSignal(Direction.WEST), "West must be GREEN");
        assertEquals(SignalColor.RED, phase.getSignal(Direction.NORTH), "North must be RED");
        assertEquals(SignalColor.RED, phase.getSignal(Direction.SOUTH), "South must be RED");
    }

    private static void testAllRedClearance() {
        TrafficPhase phase = TrafficPhase.ALL_RED;
        for (Direction dir : Direction.values()) {
            assertEquals(SignalColor.RED, phase.getSignal(dir), dir + " must be RED during all-red clearance");
        }
    }

    private static void testGlobalConflictAvoidance() {
        for (TrafficPhase phase : TrafficPhase.values()) {
            boolean nsGo = phase.getSignal(Direction.NORTH) == SignalColor.GREEN || phase.getSignal(Direction.NORTH) == SignalColor.YELLOW;
            boolean ewGo = phase.getSignal(Direction.EAST) == SignalColor.GREEN || phase.getSignal(Direction.EAST) == SignalColor.YELLOW;
            assertFalse(nsGo && ewGo, "Conflicting directions cannot both have go/caution signals in phase " + phase);
        }
    }

    private static void testVehicleMovement() {
        Vehicle v = new Vehicle(1, Direction.NORTH, 0.0, 10.0);
        v.step(1.5);
        assertEquals(15.0, v.getPosition(), 0.001, "Vehicle should have moved 15m");
        assertEquals(0.0, v.getWaitTime(), 0.001, "Wait time should remain 0 for moving vehicle");
    }

    private static void testVehicleWaitTime() {
        Vehicle v = new Vehicle(2, Direction.SOUTH, 0.0, 10.0);
        v.setSpeed(0.0);
        v.step(3.0);
        assertEquals(0.0, v.getPosition(), 0.001, "Stopped vehicle should not change position");
        assertEquals(3.0, v.getWaitTime(), 0.001, "Stopped vehicle should accumulate wait time");
    }

    private static void testSpawningBuffer() {
        ApproachLane lane = new ApproachLane(Direction.NORTH, 200.0, 150.0, 2.5, 10.0);
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0);
        assertTrue(lane.canSpawnVehicle(), "Should allow initial spawn");
        assertTrue(lane.tryAddVehicle(v1), "Should successfully add initial vehicle");

        Vehicle v2 = new Vehicle(2, Direction.NORTH, 0.0, 10.0);
        assertFalse(lane.canSpawnVehicle(), "Should block spawn while v1 is still at 0.0");

        lane.update(2.0, SignalColor.GREEN, 2.0); // v1 moves to 20m
        assertTrue(lane.canSpawnVehicle(), "Should allow spawn once v1 has cleared safe buffer");
        assertTrue(lane.tryAddVehicle(v2), "Should successfully add second vehicle");
    }

    private static void testRedLightStopLineQueue() {
        ApproachLane lane = new ApproachLane(Direction.NORTH, 200.0, 150.0, 2.5, 10.0);
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0);
        lane.tryAddVehicle(v1);

        for (int i = 0; i < 20; i++) {
            lane.update(1.0, SignalColor.RED, i + 1);
        }

        assertEquals(150.0, v1.getPosition(), 0.001, "Vehicle must stop at stop line");
        assertTrue(v1.isStopped(), "Vehicle must be stopped");
        assertEquals(1, lane.getQueueLength(), "Queue length should be 1");
        assertEquals(5.0, lane.getMaxWaitTime(), 0.01, "Wait time should be 5 seconds");
    }

    private static void testSafeFollowingDistance() {
        ApproachLane lane = new ApproachLane(Direction.NORTH, 200.0, 150.0, 2.5, 10.0);
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0, 4.5);
        lane.tryAddVehicle(v1);
        lane.update(15.0, SignalColor.RED, 15.0); // v1 reaches 150.0

        Vehicle v2 = new Vehicle(2, Direction.NORTH, 15.0, 10.0, 4.5);
        lane.tryAddVehicle(v2);

        for (int i = 0; i < 20; i++) {
            lane.update(1.0, SignalColor.RED, 15.0 + i + 1);
        }

        // v1 rear bumper = 150.0 - 4.5 = 145.5. v2 stops at 145.5 - 2.5 = 143.0.
        assertEquals(143.0, v2.getPosition(), 0.001, "Trailing vehicle must maintain safe bumper gap");
        assertTrue(v2.isStopped(), "v2 must be stopped behind v1");
        assertEquals(2, lane.getQueueLength(), "Both vehicles must be in queue");
    }

    private static void testGreenClearance() {
        ApproachLane lane = new ApproachLane(Direction.NORTH, 200.0, 150.0, 2.5, 10.0);
        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0, 4.5);
        lane.tryAddVehicle(v1);
        lane.update(15.0, SignalColor.RED, 15.0); // at stop line

        lane.update(10.0, SignalColor.GREEN, 25.0); // travels past lane length (200m)
        assertEquals(0, lane.getVehicles().size(), "Active vehicles should be empty");
        assertEquals(1, lane.getClearedVehicleCount(), "Vehicle should be cleared");
        assertEquals(0, lane.getQueueLength(), "Queue should be 0");
    }

    private static void testIntersectionAggregate() {
        Intersection intersection = new Intersection();
        assertEquals(TrafficPhase.ALL_RED, intersection.getCurrentPhase(), "Initial phase should be ALL_RED");

        Vehicle v1 = new Vehicle(1, Direction.NORTH, 0.0, 10.0);
        Vehicle v2 = new Vehicle(2, Direction.WEST, 0.0, 10.0);
        intersection.getApproach(Direction.NORTH).tryAddVehicle(v1);
        intersection.getApproach(Direction.WEST).tryAddVehicle(v2);

        for (int i = 0; i < 20; i++) {
            intersection.update(1.0, i + 1);
        }

        assertEquals(1, intersection.getQueueLength(Direction.NORTH), "North queue should be 1");
        assertEquals(1, intersection.getQueueLength(Direction.WEST), "West queue should be 1");
        assertEquals(2, intersection.getTotalQueueLength(), "Total queue should be 2");
    }
}
