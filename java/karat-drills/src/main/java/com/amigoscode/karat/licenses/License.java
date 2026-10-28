package com.amigoscode.karat.licenses;

/**
 * A single floating license in the shared pool and its current checkout state. Status is
 * "AVAILABLE" or "IN_USE". checkedOutAt is the time the license was assigned to a user and
 * lastHeartbeatAt the most recent time the client confirmed it is still using it.
 */
public class License {
    public final int licenseId;
    public String status;
    public String assignedUserId;
    public Integer checkedOutAt;
    public Integer lastHeartbeatAt;

    public License(int licenseId, String status) {
        this(licenseId, status, null, null, null);
    }

    public License(int licenseId, String status, String assignedUserId) {
        this(licenseId, status, assignedUserId, null, null);
    }

    public License(
            int licenseId,
            String status,
            String assignedUserId,
            Integer checkedOutAt,
            Integer lastHeartbeatAt) {
        this.licenseId = licenseId;
        this.status = status;
        this.assignedUserId = assignedUserId;
        this.checkedOutAt = checkedOutAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    @Override
    public String toString() {
        return "License " + licenseId + " " + status + " user=" + assignedUserId
                + " checkedOutAt=" + checkedOutAt + " lastHeartbeatAt=" + lastHeartbeatAt;
    }
}
