package com.amigoscode.events;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventRegistrationTest {

    @Test
    void projectIsWiredUp() {
        assertThat(EventRegistration.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void createsAnEventAndRegistersAParticipant() {
        EventRegistration registration = new EventRegistration();

        assertThat(registration.addEvent("java-101", "WORKSHOP", 2, "")).isTrue();
        assertThat(registration.registerParticipant(
                "java-101", "alice", "alice@example.com", "BEGINNER")).isZero();
    }
}
