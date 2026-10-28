package com.amigoscode.karat.licenses;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
@Disabled("licenses drill: remove this line to begin part 1")
class LicensePoolManagerTest {

    @Test
    void isLicenseAvailable1() {
        // Test isLicenseAvailable for AVAILABLE, unassigned licenses.
        LicensePoolManager manager = new LicensePoolManager();

        // Available, unassigned license using the no-assignment constructor
        License licenseDefault = new License(1, "AVAILABLE");
        assertThat(manager.isLicenseAvailable(licenseDefault)).isTrue();

        // Available license with explicit null fields
        License licenseExplicitNull = new License(2, "AVAILABLE", null, null, null);
        assertThat(manager.isLicenseAvailable(licenseExplicitNull)).isTrue();
    }

    @Test
    void isLicenseAvailable2() {
        // Test isLicenseAvailable for IN_USE licenses, both assigned and unassigned.
        LicensePoolManager manager = new LicensePoolManager();

        // In-use license that is assigned to a user
        License licenseAssigned = new License(1, "IN_USE", "U-1", 10, 10);
        assertThat(manager.isLicenseAvailable(licenseAssigned)).isFalse();

        // IN_USE license with no assigned user.
        License licenseUnassigned = new License(3, "IN_USE", null);
        assertThat(manager.isLicenseAvailable(licenseUnassigned)).isFalse();
    }

    @Test
    void isLicenseAvailable3() {
        // Test isLicenseAvailable for AVAILABLE licenses with an assigned user.
        LicensePoolManager manager = new LicensePoolManager();

        // Available license that still has an assigned user
        License licenseWithUser = new License(4, "AVAILABLE", "U-2");
        assertThat(manager.isLicenseAvailable(licenseWithUser)).isFalse();

        // AVAILABLE license with an empty string assigned user.
        License licenseEmptyUser = new License(5, "AVAILABLE", "");
        assertThat(manager.isLicenseAvailable(licenseEmptyUser)).isFalse();
    }

    @Test
    void isLicenseAvailable4() {
        // Test isLicenseAvailable across a pool with mixed statuses and assignments.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "AVAILABLE"));
        manager.licenses.put(2, new License(2, "IN_USE", "U-1", 10, 10));
        manager.licenses.put(3, new License(3, "AVAILABLE"));
        manager.licenses.put(4, new License(4, "IN_USE", "U-2", 20, 20));

        List<Integer> availableIds = new ArrayList<>();
        for (License license : manager.licenses.values()) {
            if (manager.isLicenseAvailable(license)) {
                availableIds.add(license.licenseId);
            }
        }
        Collections.sort(availableIds);

        assertThat(availableIds).containsExactly(1, 3);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void checkoutLicense1() {
        // Test that checkoutLicense assigns an available license and sets all of its fields.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "AVAILABLE"));

        Integer licenseId = manager.checkoutLicense("U-1", 100);

        assertThat(licenseId).isEqualTo(1);
        License license = manager.licenses.get(1);
        assertThat(license.status).isEqualTo("IN_USE");
        assertThat(license.assignedUserId).isEqualTo("U-1");
        assertThat(license.checkedOutAt).isEqualTo(100);
        assertThat(license.lastHeartbeatAt).isEqualTo(100);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void checkoutLicense2() {
        // Test checkoutLicense when multiple licenses are available.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(5, new License(5, "AVAILABLE"));
        manager.licenses.put(2, new License(2, "AVAILABLE"));

        Integer licenseId = manager.checkoutLicense("U-1", 100);

        // Multiple licenses available.
        assertThat(licenseId).isEqualTo(2);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void checkoutLicense3() {
        // Test that checkoutLicense returns null whenever there is nothing to assign: the user
        // already holds a license, no license is available, or the pool is empty.

        // No license available: the only license in the pool is already IN_USE.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "IN_USE", "U-2"));

        Integer licenseId = manager.checkoutLicense("U-1", 100);

        assertThat(licenseId).isNull();

        // User already holds a license, and another license is available.
        manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "IN_USE", "U-1"));
        manager.licenses.put(2, new License(2, "AVAILABLE"));

        licenseId = manager.checkoutLicense("U-1", 100);

        assertThat(licenseId).isNull();
        // The available license must remain untouched.
        assertThat(manager.licenses.get(2).status).isEqualTo("AVAILABLE");

        // Empty pool: there are no licenses at all.
        manager = new LicensePoolManager();

        licenseId = manager.checkoutLicense("U-1", 100);

        assertThat(licenseId).isNull();
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void checkoutLicense4() {
        // Test that checkoutLicense skips a lower-id license already IN_USE and held by a
        // different user, assigning the available higher-id license instead.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "IN_USE", "U-1"));
        manager.licenses.put(2, new License(2, "AVAILABLE"));

        Integer licenseId = manager.checkoutLicense("U-3", 200);

        assertThat(licenseId).isEqualTo(2);
        License license2 = manager.licenses.get(2);
        assertThat(license2.status).isEqualTo("IN_USE");
        assertThat(license2.assignedUserId).isEqualTo("U-3");
        assertThat(license2.checkedOutAt).isEqualTo(200);
        assertThat(license2.lastHeartbeatAt).isEqualTo(200);

        // The lower-id license, held by a different user, must remain untouched.
        License license1 = manager.licenses.get(1);
        assertThat(license1.status).isEqualTo("IN_USE");
        assertThat(license1.assignedUserId).isEqualTo("U-1");
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void returnLicense1() {
        // Test that returnLicense clears an in-use license's fields and makes it available.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "IN_USE", "U-1", 100, 150));

        boolean result = manager.returnLicense("U-1");

        assertThat(result).isTrue();
        License license = manager.licenses.get(1);
        assertThat(license.status).isEqualTo("AVAILABLE");
        assertThat(license.assignedUserId).isNull();
        assertThat(license.checkedOutAt).isNull();
        assertThat(license.lastHeartbeatAt).isNull();
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void returnLicense2() {
        // Test that returnLicense returns false when the user does not hold a license.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "AVAILABLE"));

        boolean result = manager.returnLicense("U-1");

        assertThat(result).isFalse();
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void returnLicense3() {
        // Test that a license freed by returnLicense can immediately be checked out by another user.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(1, new License(1, "IN_USE", "U-1"));

        manager.returnLicense("U-1");
        Integer licenseId = manager.checkoutLicense("U-2", 200);

        assertThat(licenseId).isEqualTo(1);
        assertThat(manager.licenses.get(1).assignedUserId).isEqualTo("U-2");
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void returnLicense4() {
        // Test returnLicense with an unrelated license present, followed by a checkoutLicense call.
        LicensePoolManager manager = new LicensePoolManager();
        manager.licenses.put(5, new License(5, "IN_USE", "U-1", 100, 150));
        manager.licenses.put(2, new License(2, "AVAILABLE"));

        boolean result = manager.returnLicense("U-1");

        assertThat(result).isTrue();
        License license5 = manager.licenses.get(5);
        assertThat(license5.status).isEqualTo("AVAILABLE");
        assertThat(license5.assignedUserId).isNull();
        assertThat(license5.checkedOutAt).isNull();
        assertThat(license5.lastHeartbeatAt).isNull();

        // Unrelated license present.
        License license2 = manager.licenses.get(2);
        assertThat(license2.status).isEqualTo("AVAILABLE");
        assertThat(license2.assignedUserId).isNull();
        assertThat(license2.checkedOutAt).isNull();
        assertThat(license2.lastHeartbeatAt).isNull();

        // Checkout after return.
        Integer licenseId = manager.checkoutLicense("U-2", 200);

        assertThat(licenseId).isEqualTo(2);
        assertThat(manager.licenses.get(5).status).isEqualTo("AVAILABLE");
    }
}
