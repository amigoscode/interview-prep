package com.amigoscode.karat.gym;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public final List<Member> members = new ArrayList<>();

    public void addMember(Member member) {
        members.add(member);
    }

    public void updateMembership(int memberId, MembershipStatus membershipStatus) {
        for (Member member : members) {
            if (member.memberId == memberId) {
                member.membershipStatus = membershipStatus;
                break;
            }
        }
    }

    /**
     * Returns the number of members, the number of paid members (SILVER or GOLD) and the
     * conversion rate: paid members as a percentage of all members, for example 66.67.
     */
    public MembershipStatistics getMembershipStatistics() {
        int totalMembers = members.size();
        int totalPaidMembers = 0;
        for (Member member : members) {
            if (member.membershipStatus == MembershipStatus.GOLD
                    || member.membershipStatus == MembershipStatus.SILVER) {
                totalPaidMembers++;
            }
        }
        double conversionRate = (totalPaidMembers / totalMembers) * 100.0;
        return new MembershipStatistics(totalMembers, totalPaidMembers, conversionRate);
    }

    /**
     * Part 2.1: records a workout session for a member. If the member does not exist the workout
     * is ignored.
     */
    public void addWorkout(int memberId, Workout workout) {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: the average workout duration in minutes for each member, as a map from member ID
     * to average.
     */
    public Map<Integer, Double> getAverageWorkoutDurations() {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 3: the total monthly revenue from memberships, in cents. BRONZE is free, SILVER costs
     * 29.99 a month and GOLD 49.99 a month.
     */
    public long getTotalMonthlyRevenueCents() {
        throw new UnsupportedOperationException("part 3");
    }
}
