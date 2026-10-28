package com.amigoscode.karat.crm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class CrmDataQualityTest {

    @Test
    void customerRecord() {
        CustomerRecord r = new CustomerRecord(1, "Alice Smith", "alice@example.com", "5551234567", "New York", 10);

        assertThat(r.recordId).isEqualTo(1);
        assertThat(r.name).isEqualTo("Alice Smith");
        assertThat(r.email).isEqualTo("alice@example.com");
        assertThat(r.phone).isEqualTo("5551234567");
        assertThat(r.city).isEqualTo("New York");
        assertThat(r.signupDay).isEqualTo(10);
    }

    @Test
    void findByEmail() {
        DataQualityEngine engine = new DataQualityEngine();
        // Record 3 inserted before record 1 to enforce sorting.
        engine.addRecord(new CustomerRecord(3, "Alice Doe", "Alice@Example.Com", "5551112222", "New York", 20));
        engine.addRecord(new CustomerRecord(1, "Alice Smith", "alice@example.com", "5551234567", "New York", 10));
        engine.addRecord(new CustomerRecord(2, "Bob Jones", "BOB@EXAMPLE.COM", "5559876543", "Chicago", 15));
        engine.addRecord(new CustomerRecord(4, "Charlie Brown", "charlie@test.com", "5553334444", "Boston", 25));

        assertThat(engine.findByEmail("ALICE@EXAMPLE.COM")).containsExactly(1, 3);
        assertThat(engine.findByEmail("bob@example.com")).containsExactly(2);
        assertThat(engine.findByEmail("nobody@test.com")).isEmpty();
    }

    @Test
    void findByEmailOnAnEmptyEngineFindsNothing() {
        assertThat(new DataQualityEngine().findByEmail("alice@example.com")).isEmpty();
        assertThat(new DataQualityEngine().getRecord(1)).isNull();
    }

    @Test
    void validateRecord() {
        DataQualityEngine engine = new DataQualityEngine();
        engine.addRecord(new CustomerRecord(1, "Alice Smith", "alice@example.com", "5551234567", "New York", 10));
        engine.addRecord(new CustomerRecord(2, " ", "bobexample.com", "555-987-6543", "Chicago", 15));
        engine.addRecord(new CustomerRecord(3, "Charlie", "charlie@@test.com", "5551112222", "Boston", 20));
        engine.addRecord(new CustomerRecord(4, "", "valid@email.com", "abc1234567", "Denver", 25));
        engine.addRecord(new CustomerRecord(5, "Eve Adams", "eve@test.com", "", "Seattle", 30));

        assertThat(engine.validateRecord(1)).isEmpty();
        assertThat(engine.validateRecord(2)).containsExactly("email", "name", "phone");
        assertThat(engine.validateRecord(3)).containsExactly("email");
        assertThat(engine.validateRecord(4)).containsExactly("name", "phone");
        assertThat(engine.validateRecord(5)).containsExactly("phone");
        assertThat(engine.validateRecord(999)).isNull();
    }

    @Test
    void validateRecordRejectsNonAsciiDigitsAndNullFields() {
        DataQualityEngine engine = new DataQualityEngine();
        engine.addRecord(new CustomerRecord(1, "Nadia", "nadia@test.com", "١٢٣", "Cairo", 10)); // Arabic-Indic digits
        engine.addRecord(new CustomerRecord(2, null, null, null, "Oslo", 11));
        engine.addRecord(new CustomerRecord(3, "Sam", "sam@test.com", " 5551234567", "Perth", 12)); // leading space

        assertThat(engine.validateRecord(1)).containsExactly("phone");
        assertThat(engine.validateRecord(2)).containsExactly("email", "name", "phone");
        assertThat(engine.validateRecord(3)).containsExactly("phone");
    }

    @Test
    void citySummary() {
        DataQualityEngine engine = new DataQualityEngine();
        engine.addRecord(new CustomerRecord(1, "Alice", "a@test.com", "1234567890", "New York", 10));
        engine.addRecord(new CustomerRecord(2, "Bob", "b@test.com", "2345678901", "new york", 15));
        engine.addRecord(new CustomerRecord(3, "Charlie", "c@test.com", "3456789012", "Chicago", 20));
        engine.addRecord(new CustomerRecord(4, "Diana", "d@test.com", "4567890123", " NEW YORK ", 25));
        engine.addRecord(new CustomerRecord(5, "Eve", "e@test.com", "5678901234", "chicago", 30));
        engine.addRecord(new CustomerRecord(6, "Frank", "f@test.com", "6789012345", "Boston", 35));

        assertThat(engine.getCitySummary())
                .containsExactlyInAnyOrderEntriesOf(Map.of("new york", 3, "chicago", 2, "boston", 1));
        assertThat(new DataQualityEngine().getCitySummary()).isEmpty();
    }

    @Test
    void blankCitiesAreCountedUnderTheEmptyKey() {
        DataQualityEngine engine = new DataQualityEngine();
        engine.addRecord(new CustomerRecord(1, "Alice", "a@test.com", "1234567890", "", 10));
        engine.addRecord(new CustomerRecord(2, "Bob", "b@test.com", "2345678901", "   ", 15));
        engine.addRecord(new CustomerRecord(3, "Charlie", "c@test.com", "3456789012", "Boston", 20));

        assertThat(engine.getCitySummary()).containsExactlyInAnyOrderEntriesOf(Map.of("", 2, "boston", 1));
    }
}
