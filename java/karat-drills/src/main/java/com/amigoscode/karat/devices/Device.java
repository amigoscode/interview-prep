package com.amigoscode.karat.devices;

/**
 * A single piece of IoT hardware deployed at a location, with its identifying and lifecycle
 * attributes. Days are day numbers relative to a fixed reference point.
 */
public class Device {
    public final String deviceId;
    public final String deviceType;
    public final String location;
    public String status;
    public final int registeredDay;
    public int nextMaintenanceDay;
    public int failureCount;

    public Device(
            String deviceId,
            String deviceType,
            String location,
            String status,
            int registeredDay,
            int nextMaintenanceDay,
            int failureCount) {
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.location = location;
        this.status = status;
        this.registeredDay = registeredDay;
        this.nextMaintenanceDay = nextMaintenanceDay;
        this.failureCount = failureCount;
    }

    @Override
    public String toString() {
        return "Device " + deviceId + " [" + deviceType + "] at " + location + " status=" + status;
    }
}
