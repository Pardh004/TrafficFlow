package com.trafficflow.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SimulationSnapshotTest {

    private Map<Direction, SignalColor> sourceSignals;
    private Map<Direction, Integer> sourceQueues;
    private Map<Direction, Double> sourceWaits;
    private Map<Direction, List<VehicleSnapshot>> sourceVehicles;
    private SimulationSnapshot snapshot;

    @BeforeEach
    void setUp() {
        sourceSignals = new EnumMap<>(Direction.class);
        sourceQueues = new EnumMap<>(Direction.class);
        sourceWaits = new EnumMap<>(Direction.class);
        sourceVehicles = new EnumMap<>(Direction.class);

        for (Direction dir : Direction.values()) {
            sourceSignals.put(dir, SignalColor.RED);
            sourceQueues.put(dir, 0);
            sourceWaits.put(dir, 0.0);
            sourceVehicles.put(dir, new ArrayList<>());
        }

        sourceSignals.put(Direction.NORTH, SignalColor.GREEN);
        sourceQueues.put(Direction.EAST, 3);
        sourceWaits.put(Direction.EAST, 14.5);

        VehicleSnapshot v1 = new VehicleSnapshot(1, Direction.EAST, 130.0, 0.0, 4.5, 14.5, true, false);
        sourceVehicles.get(Direction.EAST).add(v1);

        snapshot = new SimulationSnapshot(
                25.0,
                TrafficPhase.NS_GREEN,
                sourceSignals,
                sourceQueues,
                sourceWaits,
                sourceVehicles,
                10,
                5,
                3,
                6.2,
                14.5,
                "Adaptive Controller",
                false,
                false
        );
    }

    @Test
    @DisplayName("Signals map cannot be modified externally")
    void testSignalsMapImmutability() {
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.signals().put(Direction.NORTH, SignalColor.YELLOW));
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.signals().clear());
    }

    @Test
    @DisplayName("QueueLengths map cannot be modified externally")
    void testQueueLengthsMapImmutability() {
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.queueLengths().put(Direction.SOUTH, 99));
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.queueLengths().clear());
    }

    @Test
    @DisplayName("MaxWaitTimes map cannot be modified externally")
    void testMaxWaitTimesMapImmutability() {
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.maxWaitTimes().put(Direction.WEST, 50.0));
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.maxWaitTimes().clear());
    }

    @Test
    @DisplayName("Outer activeVehicles map cannot be modified externally")
    void testActiveVehiclesMapImmutability() {
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.activeVehicles().put(Direction.NORTH, new ArrayList<>()));
        assertThrows(UnsupportedOperationException.class, () ->
                snapshot.activeVehicles().clear());
    }

    @Test
    @DisplayName("Individual activeVehicles lists cannot be modified externally")
    void testActiveVehiclesListImmutability() {
        List<VehicleSnapshot> eastList = snapshot.activeVehicles().get(Direction.EAST);
        assertNotNull(eastList);

        VehicleSnapshot v2 = new VehicleSnapshot(2, Direction.EAST, 120.0, 0.0, 4.5, 5.0, true, false);
        assertThrows(UnsupportedOperationException.class, () ->
                eastList.add(v2));
        assertThrows(UnsupportedOperationException.class, () ->
                eastList.clear());
    }

    @Test
    @DisplayName("Modifying source collections after snapshot creation does not alter the snapshot")
    void testDefensiveCopyIntegrity() {
        // Mutate source signals
        sourceSignals.put(Direction.NORTH, SignalColor.RED);
        assertEquals(SignalColor.GREEN, snapshot.signals().get(Direction.NORTH),
                "Snapshot signals must remain GREEN despite source map mutation");

        // Mutate source queues
        sourceQueues.put(Direction.EAST, 999);
        assertEquals(3, snapshot.queueLengths().get(Direction.EAST),
                "Snapshot queues must remain 3 despite source map mutation");

        // Mutate source waits
        sourceWaits.put(Direction.EAST, 500.0);
        assertEquals(14.5, snapshot.maxWaitTimes().get(Direction.EAST),
                "Snapshot maxWaitTimes must remain 14.5 despite source map mutation");

        // Mutate source vehicle list
        VehicleSnapshot vExtra = new VehicleSnapshot(99, Direction.EAST, 50.0, 10.0, 4.5, 0.0, false, false);
        sourceVehicles.get(Direction.EAST).add(vExtra);

        assertEquals(1, snapshot.activeVehicles().get(Direction.EAST).size(),
                "Snapshot activeVehicles list must remain size 1 despite source list addition");
    }
}
