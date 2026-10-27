package com.amigoscode.events;

/** Story 1 starts here. Keep confirmed registrations and the waitlist separate. */
public final class EventRegistration {

    public boolean addEvent(String eventId, String eventType, int capacity, String requirements) {
        throw new UnsupportedOperationException("story 1");
    }

    public int registerParticipant(String eventId, String participantId, String email, String skillLevel) {
        throw new UnsupportedOperationException("story 1");
    }

    public boolean cancelRegistration(String eventId, String participantId) {
        throw new UnsupportedOperationException("story 3");
    }

    public String[] findRegisteredEvents(String participantId) {
        throw new UnsupportedOperationException("story 4");
    }
}
