package com.amigoscode.karat.devices;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class DeviceLifecycleManagerTest {

    @Test
    void updateDeviceStatus1() {
        // ACTIVE -> SUSPENDED: valid transition.
        DeviceLifecycleManager managerA = new DeviceLifecycleManager();
        managerA.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "ACTIVE", 10, 50, 1));
        boolean resultA = managerA.updateDeviceStatus("D-1", "SUSPENDED");
        assertThat(resultA).isTrue();
        assertThat(managerA.devices.get("D-1").status).isEqualTo("SUSPENDED");

        // MAINTENANCE_DUE -> ACTIVE.
        DeviceLifecycleManager managerB = new DeviceLifecycleManager();
        managerB.devices.put("D-3", new Device("D-3", "GATEWAY", "WAREHOUSE-B", "MAINTENANCE_DUE", 1, 15, 2));
        boolean resultB = managerB.updateDeviceStatus("D-3", "ACTIVE");
        assertThat(resultB).isTrue();
        assertThat(managerB.devices.get("D-3").status).isEqualTo("ACTIVE");

        // REGISTERED -> RETIRED.
        DeviceLifecycleManager managerC = new DeviceLifecycleManager();
        managerC.devices.put("D-4", new Device("D-4", "SENSOR", "WAREHOUSE-B", "REGISTERED", 3, 30, 0));
        boolean resultC = managerC.updateDeviceStatus("D-4", "RETIRED");
        assertThat(resultC).isTrue();
        assertThat(managerC.devices.get("D-4").status).isEqualTo("RETIRED");

        // SUSPENDED -> ACTIVE.
        DeviceLifecycleManager managerD = new DeviceLifecycleManager();
        managerD.devices.put("D-5", new Device("D-5", "CAMERA", "WAREHOUSE-A", "SUSPENDED", 7, 40, 1));
        boolean resultD = managerD.updateDeviceStatus("D-5", "ACTIVE");
        assertThat(resultD).isTrue();
        assertThat(managerD.devices.get("D-5").status).isEqualTo("ACTIVE");
    }

    @Test
    void updateDeviceStatus2() {
        // ACTIVE -> REGISTERED: invalid transition.
        DeviceLifecycleManager managerA = new DeviceLifecycleManager();
        managerA.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "ACTIVE", 10, 50, 1));
        boolean resultA = managerA.updateDeviceStatus("D-1", "REGISTERED");
        assertThat(resultA).isFalse();
        assertThat(managerA.devices.get("D-1").status).isEqualTo("ACTIVE");

        // RETIRED device.
        DeviceLifecycleManager managerB = new DeviceLifecycleManager();
        managerB.devices.put("D-2", new Device("D-2", "CAMERA", "WAREHOUSE-A", "RETIRED", 5, 20, 4));
        boolean resultB = managerB.updateDeviceStatus("D-2", "ACTIVE");
        assertThat(resultB).isFalse();
        assertThat(managerB.devices.get("D-2").status).isEqualTo("RETIRED");

        // Same-status transition.
        DeviceLifecycleManager managerC = new DeviceLifecycleManager();
        managerC.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "ACTIVE", 10, 50, 1));
        boolean resultC = managerC.updateDeviceStatus("D-1", "ACTIVE");
        assertThat(resultC).isFalse();
        assertThat(managerC.devices.get("D-1").status).isEqualTo("ACTIVE");
    }

    @Test
    void updateDeviceStatus3() {
        // Unknown device ID.
        DeviceLifecycleManager managerA = new DeviceLifecycleManager();
        boolean resultA = managerA.updateDeviceStatus("UNKNOWN", "ACTIVE");
        assertThat(resultA).isFalse();

        // Unrecognized status.
        DeviceLifecycleManager managerB = new DeviceLifecycleManager();
        managerB.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "ACTIVE", 10, 50, 1));
        boolean resultB = managerB.updateDeviceStatus("D-1", "BROKEN");
        assertThat(resultB).isFalse();
        assertThat(managerB.devices.get("D-1").status).isEqualTo("ACTIVE");
    }

    @Test
    void everyStatusCanRetireExceptRetired() {
        for (String status : new String[] {"REGISTERED", "ACTIVE", "SUSPENDED", "MAINTENANCE_DUE"}) {
            DeviceLifecycleManager manager = new DeviceLifecycleManager();
            manager.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", status, 1, 10, 0));
            assertThat(manager.updateDeviceStatus("D-1", "RETIRED")).isTrue();
            assertThat(manager.devices.get("D-1").status).isEqualTo("RETIRED");
        }

        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        manager.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "RETIRED", 1, 10, 0));
        assertThat(manager.updateDeviceStatus("D-1", "RETIRED")).isFalse();
    }

    @Test
    void nullStatusIsRejectedWithoutChangingTheDevice() {
        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        manager.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "ACTIVE", 1, 10, 0));

        assertThat(manager.updateDeviceStatus("D-1", null)).isFalse();
        assertThat(manager.devices.get("D-1").status).isEqualTo("ACTIVE");
    }

    @Test
    void registerDeviceStoresASupportedNewDevice() {
        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        Device device = new Device("D-1", "SENSOR", "WAREHOUSE-A", "REGISTERED", 1, 30, 0);

        assertThat(manager.registerDevice(device)).isTrue();
        assertThat(manager.devices).containsOnlyKeys("D-1");
        assertThat(manager.devices.get("D-1")).isSameAs(device);

        assertThat(manager.registerDevice(new Device("D-2", "GATEWAY", "WAREHOUSE-B", "REGISTERED", 2, 40, 0))).isTrue();
        assertThat(manager.devices).containsOnlyKeys("D-1", "D-2");
    }

    @Test
    void registerDeviceRejectsUnsupportedDuplicateAndNonRegisteredDevices() {
        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        Device original = new Device("D-1", "SENSOR", "WAREHOUSE-A", "REGISTERED", 1, 30, 0);
        manager.registerDevice(original);

        // Unsupported device type.
        assertThat(manager.registerDevice(new Device("D-2", "DRONE", "WAREHOUSE-A", "REGISTERED", 1, 30, 0))).isFalse();
        assertThat(manager.devices).doesNotContainKey("D-2");

        // Same ID as a device already in the fleet: the original stays.
        assertThat(manager.registerDevice(new Device("D-1", "CAMERA", "WAREHOUSE-B", "REGISTERED", 5, 60, 0))).isFalse();
        assertThat(manager.devices.get("D-1")).isSameAs(original);

        // A new device must start in REGISTERED.
        assertThat(manager.registerDevice(new Device("D-3", "CAMERA", "WAREHOUSE-B", "ACTIVE", 5, 60, 0))).isFalse();
        assertThat(manager.devices).containsOnlyKeys("D-1");
    }

    @Test
    void devicesDueForMaintenanceListsActiveDevicesInIdOrder() {
        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        manager.devices.put("D-3", new Device("D-3", "SENSOR", "WAREHOUSE-A", "ACTIVE", 1, 10, 0));
        manager.devices.put("D-1", new Device("D-1", "CAMERA", "WAREHOUSE-A", "ACTIVE", 1, 20, 2));
        manager.devices.put("D-2", new Device("D-2", "GATEWAY", "WAREHOUSE-B", "ACTIVE", 1, 40, 0));
        manager.devices.put("D-4", new Device("D-4", "SENSOR", "WAREHOUSE-B", "SUSPENDED", 1, 5, 1)); // not ACTIVE
        manager.devices.put("D-5", new Device("D-5", "SENSOR", "WAREHOUSE-B", "REGISTERED", 1, 5, 0)); // not ACTIVE

        assertThat(manager.getDevicesDueForMaintenance(20)).containsExactly("D-1", "D-3");
        assertThat(manager.getDevicesDueForMaintenance(100)).containsExactly("D-1", "D-2", "D-3");
    }

    @Test
    void devicesDueForMaintenanceIsEmptyWhenNothingIsDue() {
        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        assertThat(manager.getDevicesDueForMaintenance(50)).isEmpty();

        manager.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "ACTIVE", 1, 60, 0));
        manager.devices.put("D-2", new Device("D-2", "CAMERA", "WAREHOUSE-A", "RETIRED", 1, 10, 0));
        assertThat(manager.getDevicesDueForMaintenance(50)).isEmpty();
    }

    @Test
    void deviceOnItsMaintenanceDayIsDue() {
        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        manager.devices.put("D-1", new Device("D-1", "SENSOR", "WAREHOUSE-A", "ACTIVE", 1, 30, 0));

        assertThat(manager.getDevicesDueForMaintenance(29)).isEmpty();
        assertThat(manager.getDevicesDueForMaintenance(30)).containsExactly("D-1");
    }

    @Test
    void registeredDeviceBecomesDueOnceActivated() {
        DeviceLifecycleManager manager = new DeviceLifecycleManager();
        manager.registerDevice(new Device("D-1", "SENSOR", "WAREHOUSE-A", "REGISTERED", 1, 5, 0));

        assertThat(manager.getDevicesDueForMaintenance(10)).isEmpty();
        manager.updateDeviceStatus("D-1", "ACTIVE");
        assertThat(manager.getDevicesDueForMaintenance(10)).containsExactly("D-1");
    }
}
