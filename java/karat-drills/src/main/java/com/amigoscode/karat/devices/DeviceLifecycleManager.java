package com.amigoscode.karat.devices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * We are building Device Fleet, a back-end system that manages the lifecycle of IoT devices across
 * multiple locations. A device has a deviceId, deviceType, location, status, registeredDay,
 * nextMaintenanceDay and failureCount. A device's status is one of REGISTERED, ACTIVE, SUSPENDED,
 * MAINTENANCE_DUE and RETIRED, and only the transitions in {@link #VALID_TRANSITIONS} are allowed.
 *
 * <p>Part 1: {@code DeviceLifecycleManagerTest} is not passing because of a bug in this class. Read
 * the code, run the test, fix the bug.
 *
 * <p>Part 2 (our prediction, not from the question bank): we are extending the fleet. Implement
 * {@link #registerDevice} and {@link #getDevicesDueForMaintenance}.
 */
public class DeviceLifecycleManager {

    static final Set<String> SUPPORTED_DEVICE_TYPES = new HashSet<>(
            Arrays.asList("SENSOR", "CAMERA", "GATEWAY"));

    static final Set<String> VALID_STATUSES = new HashSet<>(
            Arrays.asList("REGISTERED", "ACTIVE", "SUSPENDED", "MAINTENANCE_DUE", "RETIRED"));

    static final Map<String, Set<String>> VALID_TRANSITIONS = new HashMap<>();

    static {
        VALID_TRANSITIONS.put("REGISTERED", new HashSet<>(Arrays.asList("ACTIVE", "RETIRED")));
        VALID_TRANSITIONS.put("ACTIVE", new HashSet<>(Arrays.asList("SUSPENDED", "MAINTENANCE_DUE", "RETIRED")));
        VALID_TRANSITIONS.put("SUSPENDED", new HashSet<>(Arrays.asList("ACTIVE", "RETIRED")));
        VALID_TRANSITIONS.put("MAINTENANCE_DUE", new HashSet<>(Arrays.asList("ACTIVE", "RETIRED")));
        VALID_TRANSITIONS.put("RETIRED", new HashSet<>());
    }

    /** The registered devices, keyed by device ID. */
    public final Map<String, Device> devices = new HashMap<>();

    /** Updates the device's status if the transition is valid, and returns whether the update was applied. */
    public boolean updateDeviceStatus(String deviceId, String newStatus) {
        Device device = devices.get(deviceId);
        if (device == null) {
            return false;
        }
        // Bug was checking only that the status name exists, never that the transition is allowed.
        if (!VALID_TRANSITIONS.get(device.status).contains(newStatus)) {
            return false;
        }
        device.status = newStatus;
        return true;
    }

    /**
     * Part 2.1: registers a device in the fleet and returns whether it was stored. A device is
     * stored only if its device type is one of {@link #SUPPORTED_DEVICE_TYPES}, no device with the
     * same ID is already registered, and its status is REGISTERED. Otherwise nothing changes.
     */
    public boolean registerDevice(Device device) {
        if (!SUPPORTED_DEVICE_TYPES.contains(device.deviceType)
                || devices.containsKey(device.deviceId)
                || !"REGISTERED".equals(device.status)) {
            return false;
        }
        devices.put(device.deviceId, device);
        return true;
    }

    /**
     * Part 2.2: the IDs of the devices that are due for maintenance on the given day: the devices
     * whose status is ACTIVE and whose nextMaintenanceDay is on or before currentDay. Sorted in
     * ascending order.
     */
    public List<String> getDevicesDueForMaintenance(int currentDay) {
        List<String> due = new ArrayList<>();
        for (Device device : devices.values()) {
            if ("ACTIVE".equals(device.status) && device.nextMaintenanceDay <= currentDay) {
                due.add(device.deviceId);
            }
        }
        due.sort(null);
        return due;
    }
}
