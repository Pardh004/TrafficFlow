package com.trafficflow.engine;

import com.trafficflow.config.ScenarioPreset;
import com.trafficflow.config.SimulationConfig;
import com.trafficflow.controller.AdaptiveController;
import com.trafficflow.controller.FixedTimeController;
import com.trafficflow.model.Direction;
import com.trafficflow.model.SignalColor;
import com.trafficflow.model.SimulationSnapshot;
import com.trafficflow.model.TrafficPhase;
import com.trafficflow.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationEngineTest {

    private SimulationConfig config;
    private FixedTimeController fixedController;
    private AdaptiveController adaptiveController;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        config = SimulationConfig.builder()
                .duration(60.0)
                .timeStep(0.1)
                .arrivalRate(Direction.NORTH, 20.0)
                .arrivalRate(Direction.SOUTH, 20.0)
                .arrivalRate(Direction.EAST, 10.0)
                .arrivalRate(Direction.WEST, 10.0)
                .seed(42L)
                .build();

        fixedController = new FixedTimeController(20.0, 20.0, 4.0, 2.0);
        adaptiveController = new AdaptiveController(10.0, 40.0, 4.0, 2.0, 25.0, 1.0, 0.5, 5.0);
        engine = new SimulationEngine(config, fixedController);
    }

    @Test
    @DisplayName("Engine initializes in READY state at time 0.0")
    void testInitialState() {
        assertEquals(0.0, engine.getCurrentSimTime(), 0.001);
        assertEquals(SimulationEngine.SimulationState.READY, engine.getState());
        assertFalse(engine.isRunning());
        assertFalse(engine.isPaused());
        assertFalse(engine.isCompleted());
        assertEquals(1.0, engine.getSpeedMultiplier());
    }

    @Test
    @DisplayName("Simulation clock advances strictly by configured fixed time step")
    void testClockAdvancement() {
        engine.start();
        assertTrue(engine.isRunning());

        engine.step();
        assertEquals(0.1, engine.getCurrentSimTime(), 0.001);

        for (int i = 0; i < 9; i++) {
            engine.step();
        }
        assertEquals(1.0, engine.getCurrentSimTime(), 0.001);
    }

    @Test
    @DisplayName("Pause and resume controls execution without corrupting state")
    void testPauseAndResume() {
        engine.start();
        engine.step();
        engine.step();
        assertEquals(0.2, engine.getCurrentSimTime(), 0.001);

        // Pause
        engine.pause();
        assertTrue(engine.isPaused());
        assertFalse(engine.isRunning());

        // Calling update while paused should NOT advance time
        engine.update(1.0);
        assertEquals(0.2, engine.getCurrentSimTime(), 0.001);

        // Resume
        engine.resume();
        assertTrue(engine.isRunning());
        engine.step();
        assertEquals(0.3, engine.getCurrentSimTime(), 0.001);
    }

    @Test
    @DisplayName("Simulation marks COMPLETED when duration threshold is reached")
    void testSimulationCompletion() {
        // Small 2-second simulation config
        SimulationConfig shortConfig = SimulationConfig.builder()
                .duration(2.0)
                .timeStep(0.5)
                .build();

        SimulationEngine shortEngine = new SimulationEngine(shortConfig, fixedController);
        shortEngine.start();

        // 4 steps of 0.5s = 2.0s
        for (int i = 0; i < 4; i++) {
            shortEngine.step();
        }

        assertEquals(2.0, shortEngine.getCurrentSimTime(), 0.001);
        assertTrue(shortEngine.isCompleted());
        assertFalse(shortEngine.isRunning());

        // Further step calls must be no-ops
        shortEngine.step();
        assertEquals(2.0, shortEngine.getCurrentSimTime(), 0.001);
    }

    @Test
    @DisplayName("Reset restores initial state, clears vehicles, and resets controller & clock")
    void testReset() {
        engine.start();
        // Run 50 steps (5 seconds)
        for (int i = 0; i < 50; i++) {
            engine.step();
        }
        assertTrue(engine.getCurrentSimTime() > 0.0);

        // Reset
        engine.reset();
        assertEquals(0.0, engine.getCurrentSimTime(), 0.001);
        assertEquals(SimulationEngine.SimulationState.READY, engine.getState());
        assertEquals(0, engine.getIntersection().getTotalActiveVehicles());
        assertEquals(0, engine.getIntersection().getTotalClearedVehicles());
        assertEquals(0, engine.getSpawner().getTotalVehiclesGenerated());
        assertEquals(TrafficPhase.NS_GREEN, engine.getController().getCurrentPhase());
    }

    @Test
    @DisplayName("Vehicles remain stopped during RED signal and accumulate wait time")
    void testVehiclesStopAndQueueAtRedSignal() {
        // Force East-West to be RED (fixedController starts in NS_GREEN)
        assertEquals(SignalColor.RED, engine.getIntersection().getSignal(Direction.EAST));

        // Place vehicle on East approach 20m before stop line (stop line = 150m, pos = 130m)
        Vehicle v = new Vehicle(101, Direction.EAST, 0.0, 10.0, 4.5);
        v.setPosition(130.0);
        engine.getIntersection().getApproach(Direction.EAST).insertVehicle(v);

        // Step 20 times (2.0s at 10m/s -> travels 20m to stop line 150.0m)
        for (int i = 0; i < 20; i++) {
            engine.step();
        }

        // Vehicle should now be halted exactly at 150.0m
        assertEquals(150.0, v.getPosition(), 0.001);
        assertTrue(v.isStopped());
        assertFalse(v.hasPassedStopLine());
        assertEquals(1, engine.getIntersection().getQueueLength(Direction.EAST));

        // Step another 20 times (2.0s while stopped at RED): wait time must accumulate
        for (int i = 0; i < 20; i++) {
            engine.step();
        }
        assertTrue(v.getWaitTime() >= 1.9, "Stopped vehicle must accumulate wait time");
    }

    @Test
    @DisplayName("Vehicles move forward and clear intersection during GREEN signal")
    void testVehiclesMoveAndClearOnGreenSignal() {
        // North-South has GREEN
        assertEquals(SignalColor.GREEN, engine.getIntersection().getSignal(Direction.NORTH));

        // Insert vehicle at stop line (150m)
        Vehicle v = new Vehicle(202, Direction.NORTH, 0.0, 10.0, 4.5);
        v.setPosition(150.0);
        engine.getIntersection().getApproach(Direction.NORTH).insertVehicle(v);

        // Step 60 times (6.0s at 10m/s = 60m forward; lane length is 200m -> vehicle position reaches 210m and clears)
        for (int i = 0; i < 60; i++) {
            engine.step();
        }

        // Vehicle should have crossed stop line and cleared the lane
        assertTrue(v.hasPassedStopLine());
        assertFalse(engine.getIntersection().getApproach(Direction.NORTH).getVehicles().contains(v),
                "Vehicle v should have cleared and no longer be in active vehicles");
        assertTrue(engine.getIntersection().getAllClearedVehicles().contains(v),
                "Vehicle v must be registered in cleared vehicles");
        assertNotNull(v.getDepartureTime());
    }

    @Test
    @DisplayName("Both FixedTimeController and AdaptiveController work seamlessly with the engine")
    void testBothControllersWorkWithSameEngine() {
        // Test 1: Run with FixedTimeController
        SimulationEngine fixedEngine = new SimulationEngine(config, fixedController);
        fixedEngine.start();
        for (int i = 0; i < 100; i++) {
            fixedEngine.step();
        }
        assertEquals("Fixed-Time Controller", fixedEngine.getController().getName());
        assertTrue(fixedEngine.getCurrentSimTime() > 0.0);

        // Test 2: Run with AdaptiveController
        SimulationEngine adaptiveEngine = new SimulationEngine(config, adaptiveController);
        adaptiveEngine.start();
        for (int i = 0; i < 100; i++) {
            adaptiveEngine.step();
        }
        assertEquals("Adaptive Controller", adaptiveEngine.getController().getName());
        assertTrue(adaptiveEngine.getCurrentSimTime() > 0.0);

        // Test 3: Replace controller in READY state
        SimulationEngine freshEngine = new SimulationEngine(config, fixedController);
        assertEquals(SimulationEngine.SimulationState.READY, freshEngine.getState());
        freshEngine.setController(adaptiveController);
        assertEquals("Adaptive Controller", freshEngine.getController().getName());
        freshEngine.start();
        freshEngine.step();
        assertTrue(freshEngine.getCurrentSimTime() > 0.0);
    }

    @Test
    @DisplayName("Controller replacement is permitted only in READY state")
    void testControllerReplacementPolicy() {
        SimulationEngine testEngine = new SimulationEngine(config, fixedController);
        assertEquals(SimulationEngine.SimulationState.READY, testEngine.getState());

        // 1. Success in READY state
        testEngine.setController(adaptiveController);
        assertEquals("Adaptive Controller", testEngine.getController().getName());
        assertEquals(adaptiveController.getCurrentPhase(), testEngine.getIntersection().getCurrentPhase());

        // 2. Reject null controller
        assertThrows(NullPointerException.class, () -> testEngine.setController(null));

        // 3. Reject while RUNNING
        testEngine.start();
        assertTrue(testEngine.isRunning());
        assertThrows(IllegalStateException.class, () -> testEngine.setController(fixedController));

        // 4. Reject while PAUSED
        testEngine.pause();
        assertTrue(testEngine.isPaused());
        assertThrows(IllegalStateException.class, () -> testEngine.setController(fixedController));

        // 5. Reject while COMPLETED
        SimulationEngine shortEngine = new SimulationEngine(
                SimulationConfig.builder().duration(1.0).timeStep(0.5).build(), fixedController);
        shortEngine.start();
        shortEngine.step();
        shortEngine.step();
        assertTrue(shortEngine.isCompleted());
        assertThrows(IllegalStateException.class, () -> shortEngine.setController(adaptiveController));
    }

    @Test
    @DisplayName("Once COMPLETED, update() calls cannot advance simulation time beyond duration")
    void testCompletedEngineUpdateCannotAdvance() {
        SimulationConfig shortConfig = SimulationConfig.builder()
                .duration(2.0)
                .timeStep(0.5)
                .build();

        SimulationEngine shortEngine = new SimulationEngine(shortConfig, fixedController);
        shortEngine.start();
        shortEngine.update(2.0); // Exactly completes (4 steps)
        assertTrue(shortEngine.isCompleted());
        assertEquals(2.0, shortEngine.getCurrentSimTime(), 0.001);

        // Additional update calls while COMPLETED must be no-ops
        shortEngine.update(10.0);
        assertEquals(2.0, shortEngine.getCurrentSimTime(), 0.001);
        assertTrue(shortEngine.isCompleted());
    }

    @Test
    @DisplayName("Invalid wall-clock deltas do not mutate simulation state")
    void testInvalidWallClockDeltasAreSafeNoOps() {
        engine.start();
        engine.step();
        assertEquals(0.1, engine.getCurrentSimTime(), 0.001);

        engine.update(0.0);
        assertEquals(0.1, engine.getCurrentSimTime(), 0.001);

        engine.update(-1.0);
        assertEquals(0.1, engine.getCurrentSimTime(), 0.001);

        engine.update(Double.NaN);
        assertEquals(0.1, engine.getCurrentSimTime(), 0.001);

        engine.update(Double.POSITIVE_INFINITY);
        assertEquals(0.1, engine.getCurrentSimTime(), 0.001);

        engine.update(Double.NEGATIVE_INFINITY);
        assertEquals(0.1, engine.getCurrentSimTime(), 0.001);
    }

    @Test
    @DisplayName("Invalid speed multipliers throw IllegalArgumentException")
    void testInvalidSpeedMultipliersThrowException() {
        assertThrows(IllegalArgumentException.class, () -> engine.setSpeedMultiplier(0.0));
        assertThrows(IllegalArgumentException.class, () -> engine.setSpeedMultiplier(-2.0));
        assertThrows(IllegalArgumentException.class, () -> engine.setSpeedMultiplier(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> engine.setSpeedMultiplier(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> engine.setSpeedMultiplier(Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("Accumulator update advances simulation based on real wall-clock delta and speed multiplier")
    void testAccumulatorUpdateWithSpeedMultiplier() {
        engine.start();
        engine.setSpeedMultiplier(2.0); // 2x speed

        // 1.0 second real time * 2x = 2.0 simulation seconds (20 steps of 0.1s dt)
        engine.update(1.0);
        assertEquals(2.0, engine.getCurrentSimTime(), 0.001);

        engine.setSpeedMultiplier(5.0); // 5x speed
        // 0.2 second real time * 5x = 1.0 simulation second (10 steps)
        engine.update(0.2);
        assertEquals(3.0, engine.getCurrentSimTime(), 0.001);
    }

    @Test
    @DisplayName("createSnapshot produces an accurate, immutable simulation snapshot")
    void testSimulationSnapshotGeneration() {
        engine.start();
        for (int i = 0; i < 30; i++) {
            engine.step();
        }

        SimulationSnapshot snapshot = engine.createSnapshot();
        assertNotNull(snapshot);
        assertEquals(3.0, snapshot.simulationTime(), 0.001);
        assertEquals(TrafficPhase.NS_GREEN, snapshot.currentPhase());
        assertEquals(SignalColor.GREEN, snapshot.signals().get(Direction.NORTH));
        assertEquals(SignalColor.RED, snapshot.signals().get(Direction.EAST));
        assertEquals("Fixed-Time Controller", snapshot.controllerName());
        assertFalse(snapshot.isCompleted());
        assertFalse(snapshot.isPaused());

        for (Direction dir : Direction.values()) {
            assertNotNull(snapshot.queueLengths().get(dir));
            assertNotNull(snapshot.maxWaitTimes().get(dir));
            assertNotNull(snapshot.activeVehicles().get(dir));
        }
    }
}
