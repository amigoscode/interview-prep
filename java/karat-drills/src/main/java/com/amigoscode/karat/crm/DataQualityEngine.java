package com.amigoscode.karat.crm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * We are building the back end for a CRM data quality system. The system manages customer
 * records, each with an ID, a name, an email, a phone number, a city and a signup day, and gives
 * staff tools to validate, normalize and deduplicate them.
 *
 * <p>Part 1: {@code CrmDataQualityTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: now that the engine can search records, staff need tools to check data quality and
 * to understand the geographic distribution of customers. Implement {@link #validateRecord} and
 * {@link #getCitySummary}.
 */
public class DataQualityEngine {

    public final List<CustomerRecord> records = new ArrayList<>();

    /** Adds a customer record. */
    public void addRecord(CustomerRecord record) {
        records.add(record);
    }

    /** Returns the record with the given ID, or null if not found. */
    public CustomerRecord getRecord(int recordId) {
        for (CustomerRecord record : records) {
            if (record.recordId == recordId) {
                return record;
            }
        }
        return null;
    }

    /** Returns the IDs of all records matching the given email, case-insensitively, sorted ascending. */
    public List<Integer> findByEmail(String email) {
        List<Integer> result = new ArrayList<>();
        for (CustomerRecord record : records) {
            // Bug was a case-sensitive equals: e-mail addresses are compared ignoring case.
            if (record.email != null && record.email.equalsIgnoreCase(email)) {
                result.add(record.recordId);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Part 2.1: checks the record with the given ID against three validation rules:
     * <ul>
     *   <li>"name": the name must be non-empty after trimming whitespace.</li>
     *   <li>"email": the email must contain exactly one "@" character.</li>
     *   <li>"phone": the phone must be non-empty and contain only ASCII digit characters (0-9).</li>
     * </ul>
     * Returns the names of the fields that failed validation, sorted alphabetically; an empty list
     * if all fields are valid; null if the record does not exist.
     */
    public List<String> validateRecord(int recordId) {
        CustomerRecord record = getRecord(recordId);
        if (record == null) {
            return null;
        }
        List<String> failures = new ArrayList<>();
        if (!hasValidName(record.name)) {
            failures.add("name");
        }
        if (!hasValidEmail(record.email)) {
            failures.add("email");
        }
        if (!hasValidPhone(record.phone)) {
            failures.add("phone");
        }
        Collections.sort(failures);
        return failures;
    }

    /**
     * Part 2.2: a map from city name to the number of records in that city. City comparison
     * ignores case and leading or trailing whitespace, and the keys of the map are lowercased and
     * trimmed. Cities with no records do not appear. A record whose city is blank or
     * whitespace-only normalizes to the empty string and is counted under that "" key.
     */
    public Map<String, Integer> getCitySummary() {
        Map<String, Integer> countByCity = new HashMap<>();
        for (CustomerRecord record : records) {
            countByCity.merge(normalizeCity(record.city), 1, Integer::sum);
        }
        return countByCity;
    }

    private static boolean hasValidName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    private static boolean hasValidEmail(String email) {
        return email != null && email.chars().filter(c -> c == '@').count() == 1;
    }

    /** Character.isDigit would also accept non-ASCII digits, so the check is spelled out. */
    private static boolean hasValidPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        for (char c : phone.toCharArray()) {
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** A missing city counts the same as a blank one: the "" key. */
    private static String normalizeCity(String city) {
        return city == null ? "" : city.trim().toLowerCase();
    }
}
