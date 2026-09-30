package com.amigoscode.dispatch;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Stories 2 to 4: assigns each order to the nearest free, on-shift rider. */
public final class Dispatcher {

    public Dispatcher(Collection<Rider> riders, ShiftSchedule schedule, Clock clock) {
        throw new UnsupportedOperationException("story 2");
    }

    /** Story 2: the K closest riders that are on shift now (and, from story 3, not busy). */
    public List<Rider> nearestAvailable(Location restaurant, int k) {
        throw new UnsupportedOperationException("story 2");
    }

    public AssignmentResult assign(Order order) {
        throw new UnsupportedOperationException("story 3");
    }

    public boolean complete(String orderId) {
        throw new UnsupportedOperationException("story 3");
    }

    public Optional<Rider> riderFor(String orderId) {
        throw new UnsupportedOperationException("story 3");
    }

    public boolean isBusy(String riderId) {
        throw new UnsupportedOperationException("story 3");
    }
}
