package com.amigoscode.dispatch;

import java.time.Clock;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stories 2 to 4: assigns each order to the nearest free, on-shift rider.
 *
 * <p>Concurrency: every rider has one {@link AtomicReference} slot holding the id of the order they
 * carry, or {@code null} when free. Claiming a rider is a single {@code compareAndSet(null, orderId)},
 * so two threads can never both win the same rider, and no thread ever holds more than one rider at
 * a time, so there is no lock ordering to get wrong. A thread that loses a race simply tries the
 * next-closest candidate.
 */
public final class Dispatcher {

    private final Map<String, Rider> riders;
    private final Map<String, AtomicReference<String>> orderByRider;
    private final Map<String, String> riderByOrder = new ConcurrentHashMap<>();
    private final ShiftSchedule schedule;
    private final Clock clock;

    public Dispatcher(Collection<Rider> riders, ShiftSchedule schedule, Clock clock) {
        Objects.requireNonNull(riders, "riders");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.clock = Objects.requireNonNull(clock, "clock");

        Map<String, Rider> byId = new LinkedHashMap<>();
        Map<String, AtomicReference<String>> slots = new LinkedHashMap<>();
        for (Rider rider : riders) {
            Objects.requireNonNull(rider, "rider");
            if (byId.putIfAbsent(rider.id(), rider) != null) {
                throw new IllegalArgumentException("duplicate rider id: " + rider.id());
            }
            slots.put(rider.id(), new AtomicReference<>());
        }
        this.riders = Map.copyOf(byId);
        this.orderByRider = Map.copyOf(slots); // fixed key set: only the slots' contents change
    }

    /** Story 2: the K closest riders that are on shift now and not carrying an order. */
    public List<Rider> nearestAvailable(Location restaurant, int k) {
        LocalTime now = LocalTime.now(clock);
        return NearestRiders.find(riders.values(), restaurant, k, rider -> isAvailable(rider, now));
    }

    /** Story 3: claim the nearest available rider for {@code order}. */
    public AssignmentResult assign(Order order) {
        Objects.requireNonNull(order, "order");
        if (riderByOrder.containsKey(order.id())) {
            throw new IllegalStateException("order already assigned: " + order.id());
        }

        // Rank every free rider, not just the closest one: under contention the closest may be
        // claimed by another thread between ranking and claiming, and then we fall through to the
        // next. With 100k riders you would rank a small K and widen only if all K are lost.
        for (Rider rider : nearestAvailable(order.restaurant(), riders.size())) {
            AtomicReference<String> slot = orderByRider.get(rider.id());
            if (slot.compareAndSet(null, order.id())) {
                if (riderByOrder.putIfAbsent(order.id(), rider.id()) != null) {
                    slot.set(null); // the same order raced itself in: give the rider back
                    throw new IllegalStateException("order already assigned: " + order.id());
                }
                return new AssignmentResult.Assigned(order, rider);
            }
        }
        return new AssignmentResult.NoRiderAvailable(order);
    }

    /**
     * Story 3: the order is delivered and its rider is free again. Returns {@code false} for an
     * order that is unknown or already completed, so a retried "delivered" event is harmless.
     */
    public boolean complete(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        String riderId = riderByOrder.remove(orderId);
        if (riderId == null) {
            return false;
        }
        orderByRider.get(riderId).compareAndSet(orderId, null);
        return true;
    }

    /** The rider currently carrying {@code orderId}, if any. */
    public Optional<Rider> riderFor(String orderId) {
        Objects.requireNonNull(orderId, "orderId");
        return Optional.ofNullable(riderByOrder.get(orderId)).map(riders::get);
    }

    public boolean isBusy(String riderId) {
        AtomicReference<String> slot = orderByRider.get(riderId);
        if (slot == null) {
            throw new IllegalArgumentException("unknown rider: " + riderId);
        }
        return slot.get() != null;
    }

    private boolean isAvailable(Rider rider, LocalTime now) {
        return orderByRider.get(rider.id()).get() == null && schedule.isOnShift(rider.id(), now);
    }
}
