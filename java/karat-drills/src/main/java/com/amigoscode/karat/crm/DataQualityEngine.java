package com.amigoscode.karat.crm;

import java.util.ArrayList;
import java.util.Collections;
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
            if (record.email.equals(email)) {
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
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: a map from city name to the number of records in that city. City comparison
     * ignores case and leading or trailing whitespace, and the keys of the map are lowercased and
     * trimmed. Cities with no records do not appear. A record whose city is blank or
     * whitespace-only normalizes to the empty string and is counted under that "" key.
     */
    public Map<String, Integer> getCitySummary() {
        throw new UnsupportedOperationException("part 2");
    }
}
