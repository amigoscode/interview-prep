package com.amigoscode.karat.gym;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * We are building a program to manage a gym's membership. The gym has multiple members, each with
 * a unique ID, name and membership status. Staff can add members, update a member's status and get
 * membership statistics.
 *
 * <p>Part 1: {@code GymMembershipTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: we are adding workouts. Implement {@link #addWorkout} and
 * {@link #getAverageWorkoutDurations}. Where the workouts are stored is up to you.
 *
 * <p>Part 3: implement {@link #getTotalMonthlyRevenueCents}.
 */
public class Membership {

    private static final Map<MembershipStatus, Long> MONTHLY_FEE_CENTS = Map.of(
            MembershipStatus.BRONZE, 0L,
            MembershipStatus.SILVER, 2999L,
            MembershipStatus.GOLD, 4999L);

    public final List<Member> members = new ArrayList<>();

    /** Workouts live here, keyed by member ID, so {@link Member} stays a plain data holder. */
    private final Map<Integer, List<Workout>> workoutsByMember = new HashMap<>();

    public void addMember(Member member) {
        members.add(member);
    }

    public void updateMembership(int memberId, MembershipStatus membershipStatus) {
        findMember(memberId).ifPresent(member -> member.membershipStatus = membershipStatus);
    }

    /**
     * Returns the number of members, the number of paid members (SILVER or GOLD) and the
     * conversion rate: paid members as a percentage of all members, for example 66.67.
     */
    public MembershipStatistics getMembershipStatistics() {
        int totalMembers = members.size();
        int totalPaidMembers = 0;
        for (Member member : members) {
            if (isPaid(member.membershipStatus)) {
                totalPaidMembers++;
            }
        }
        // Bug was integer division: (4 / 6) is 0 before the cast. Divide as doubles, and guard
        // against an empty gym, where 0 / 0 is NaN.
        double conversionRate = totalMembers == 0
                ? 0.0
                : totalPaidMembers * 100.0 / totalMembers;
        return new MembershipStatistics(totalMembers, totalPaidMembers, conversionRate);
    }

    /**
     * Part 2.1: records a workout session for a member. If the member does not exist the workout
     * is ignored.
     */
    public void addWorkout(int memberId, Workout workout) {
        if (findMember(memberId).isEmpty()) {
            return;
        }
        workoutsByMember.computeIfAbsent(memberId, id -> new ArrayList<>()).add(workout);
    }

    /**
     * Part 2.2: the average workout duration in minutes for each member, as a map from member ID
     * to average. A member with no workouts has no average and is left out, so nobody divides by
     * zero.
     */
    public Map<Integer, Double> getAverageWorkoutDurations() {
        Map<Integer, Double> averages = new LinkedHashMap<>();
        for (Member member : members) {
            List<Workout> workouts = workoutsByMember.getOrDefault(member.memberId, List.of());
            if (workouts.isEmpty()) {
                continue;
            }
            double total = 0;
            for (Workout workout : workouts) {
                total += workout.getDuration();
            }
            averages.put(member.memberId, total / workouts.size());
        }
        return averages;
    }

    /**
     * Part 3: the total monthly revenue from memberships, in cents. BRONZE is free, SILVER costs
     * 29.99 a month and GOLD 49.99 a month.
     */
    public long getTotalMonthlyRevenueCents() {
        long total = 0;
        for (Member member : members) {
            total += MONTHLY_FEE_CENTS.get(member.membershipStatus);
        }
        return total;
    }

    private static boolean isPaid(MembershipStatus status) {
        return status == MembershipStatus.SILVER || status == MembershipStatus.GOLD;
    }

    private Optional<Member> findMember(int memberId) {
        return members.stream().filter(member -> member.memberId == memberId).findFirst();
    }
}
