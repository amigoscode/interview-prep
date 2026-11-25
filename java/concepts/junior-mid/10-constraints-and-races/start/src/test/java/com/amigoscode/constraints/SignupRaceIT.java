package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.constraints.support.IntegrationTest;
import com.amigoscode.constraints.support.Race;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 1. This test passes on purpose, before and after your fixes: it pins down the race in the
 * application's own check. No constraint makes that check trustworthy. A constraint only decides
 * what happens to the second insert.
 */
@Tag("task1")
class SignupRaceIT extends IntegrationTest {

    @Test
    @DisplayName("Signup: two requests with the same email are both told \"this email is free\" by the application's check (this documents the race)")
    void bothRequestsPassTheCheck() {
        work.everyonePassesTheCheckBeforeAnyoneInserts(2);

        Race.run(2, () -> signupService.signUp("ana@example.com", "Ana"));

        assertThat(work.arrivals())
                .as("requests that got past the email check and went on to insert")
                .isEqualTo(2);
    }
}
