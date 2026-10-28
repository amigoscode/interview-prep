package com.amigoscode.karat.syncqueue;

import java.util.Map;

/**
 * A person or account whose profile is mirrored in the external CRM. {@code lastSyncedValues}
 * holds, per field name, the most recent value that has actually been confirmed synced to the
 * CRM; the other fields hold the current values on our side.
 */
public class Customer {
    public final String customerId;
    public String name;
    public String email;
    public String phone;
    public String company;
    public Map<String, String> lastSyncedValues;

    public Customer(
            String customerId,
            String name,
            String email,
            String phone,
            String company,
            Map<String, String> lastSyncedValues) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.company = company;
        this.lastSyncedValues = lastSyncedValues;
    }
}
