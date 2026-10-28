package com.amigoscode.karat.licenses;

import java.util.HashMap;
import java.util.Map;

/**
 * We are building Floating License Pool, a back-end system that manages a shared pool of floating
 * software licenses for an internal engineering tools team. A license has a licenseId, a status
 * (AVAILABLE or IN_USE), an assignedUserId, a checkedOutAt time and a lastHeartbeatAt time.
 *
 * <p>Part 1: {@code LicensePoolManagerTest} is not passing because of a bug in this class. Read
 * the code, run the test, fix the bug.
 *
 * <p>Part 2: we are letting users check out and return licenses. Implement
 * {@link #checkoutLicense} and {@link #returnLicense}.
 */
public class LicensePoolManager {

    /** The pool of licenses, keyed by license ID. */
    public final Map<Integer, License> licenses = new HashMap<>();

    /** Returns whether the license can be handed out: it is AVAILABLE and nobody is assigned to it. */
    public boolean isLicenseAvailable(License license) {
        // Bug was checking the status only, so an AVAILABLE license with a leftover user looked free.
        return license.status.equals("AVAILABLE") && license.assignedUserId == null;
    }

    /**
     * Part 2.1: assigns an available license to the user and returns its ID. Rules: a user may
     * hold at most one license at a time, so the request is ignored if the user already holds one;
     * when more than one license is available the one with the lowest licenseId is assigned; the
     * license gets status IN_USE, the assigned user, and both checkedOutAt and lastHeartbeatAt set
     * to currentTime. Returns null if no license is available or the user already holds one.
     */
    public Integer checkoutLicense(String userId, Integer currentTime) {
        if (findHeldBy(userId) != null) {
            return null;
        }
        License lowest = null;
        for (License license : licenses.values()) {
            if (isLicenseAvailable(license) && (lowest == null || license.licenseId < lowest.licenseId)) {
                lowest = license;
            }
        }
        if (lowest == null) {
            return null;
        }
        lowest.status = "IN_USE";
        lowest.assignedUserId = userId;
        lowest.checkedOutAt = currentTime;
        lowest.lastHeartbeatAt = currentTime;
        return lowest.licenseId;
    }

    /**
     * Part 2.2: finds the license currently held by the user, sets its status to AVAILABLE and
     * clears the assigned user, checkout time and heartbeat time. Returns true when a license is
     * returned and false if the user does not hold one.
     */
    public boolean returnLicense(String userId) {
        License held = findHeldBy(userId);
        if (held == null) {
            return false;
        }
        held.status = "AVAILABLE";
        held.assignedUserId = null;
        held.checkedOutAt = null;
        held.lastHeartbeatAt = null;
        return true;
    }

    /** The license the user holds (IN_USE and assigned to them), or null if they hold none. */
    private License findHeldBy(String userId) {
        for (License license : licenses.values()) {
            if (license.status.equals("IN_USE") && userId.equals(license.assignedUserId)) {
                return license;
            }
        }
        return null;
    }
}
