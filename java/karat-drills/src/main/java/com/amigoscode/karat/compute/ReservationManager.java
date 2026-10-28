package com.amigoscode.karat.compute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * We are building the back end of a cloud platform that rents out compute capacity. The platform
 * has several compute pools, each in a region and each with a total capacity in capacity units.
 * Customers reserve capacity units from a pool for a period of time; a reservation is ACTIVE,
 * CANCELLED or EXPIRED. Staff can register pools, look at the reservations and ask how much
 * capacity a pool still has free.
 *
 * <p>Part 1: {@code ComputeReservationTest} is not passing because of a bug in this class. Read
 * the code, run the test, fix the bug.
 *
 * <p>Part 2: we are extending the platform to record reservations and summarise customer usage.
 * Implement {@link #addReservation} and {@link #getReservedCapacityByCustomer}.
 */
public class ReservationManager {

    public final Map<Integer, ComputePool> computePools = new HashMap<>();
    public final List<Reservation> reservations = new ArrayList<>();

    public void addComputePool(ComputePool computePool) {
        computePools.put(computePool.poolId, computePool);
    }

    /**
     * The capacity still free in a pool: the pool's total capacity minus the capacity held by
     * its ACTIVE reservations. An unknown pool has 0 available capacity.
     */
    public int getAvailableCapacity(int poolId) {
        if (!computePools.containsKey(poolId)) {
            return 0;
        }
        ComputePool pool = computePools.get(poolId);
        int usedCapacity = 0;
        for (Reservation reservation : reservations) {
            if (reservation.poolId == poolId) {
                usedCapacity += reservation.capacityUnits;
            }
        }
        return pool.totalCapacity - usedCapacity;
    }

    /**
     * Part 2.1: stores a reservation only if both conditions hold:
     * <ol>
     *   <li>the reservation's poolId refers to a known compute pool, and</li>
     *   <li>the reservation's capacityUnits is less than or equal to the pool's currently
     *       available capacity, as defined by {@link #getAvailableCapacity}.</li>
     * </ol>
     * If either condition is not met the reservation is ignored.
     */
    public void addReservation(Reservation reservation) {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: a map from customer ID to the total ACTIVE reserved capacity of that customer.
     * <ul>
     *   <li>Only ACTIVE reservations are counted.</li>
     *   <li>A customer with no ACTIVE reserved capacity does not appear in the map (so a customer
     *       whose only ACTIVE reservation is for 0 units is left out too).</li>
     *   <li>If there are no ACTIVE reservations the map is empty.</li>
     * </ul>
     */
    public Map<Integer, Integer> getReservedCapacityByCustomer() {
        throw new UnsupportedOperationException("part 2");
    }
}
