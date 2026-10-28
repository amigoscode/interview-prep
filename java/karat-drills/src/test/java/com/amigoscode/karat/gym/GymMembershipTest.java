package com.amigoscode.karat.gym;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class GymMembershipTest {

    @Test
    void member() {
        Member member = new Member(1, "John Doe", MembershipStatus.BRONZE);

        assertThat(member.memberId).isEqualTo(1);
        assertThat(member.name).isEqualTo("John Doe");
        assertThat(member.membershipStatus).isEqualTo(MembershipStatus.BRONZE);
    }

    @Test
    void membership() {
        Membership membership = new Membership();
        Member member = new Member(1, "John Doe", MembershipStatus.BRONZE);
        membership.addMember(member);

        assertThat(membership.members).hasSize(1);
        assertThat(membership.members.get(0)).isSameAs(member);

        membership.updateMembership(1, MembershipStatus.SILVER);
        assertThat(membership.members.get(0).membershipStatus).isEqualTo(MembershipStatus.SILVER);

        membership.addMember(new Member(2, "Alex C", MembershipStatus.BRONZE));
        membership.addMember(new Member(3, "Marie C", MembershipStatus.GOLD));
        membership.addMember(new Member(4, "Joe D", MembershipStatus.SILVER));
        membership.addMember(new Member(5, "June R", MembershipStatus.BRONZE));
        membership.addMember(new Member(6, "Westley D", MembershipStatus.SILVER));

        MembershipStatistics stats = membership.getMembershipStatistics();
        assertThat(stats.totalMembers).isEqualTo(6);
        assertThat(stats.totalPaidMembers).isEqualTo(4);
        assertThat(stats.conversionRate).isCloseTo(66.67, within(0.1));
    }

    @Test
    void emptyGymHasZeroConversionRate() {
        MembershipStatistics stats = new Membership().getMembershipStatistics();

        assertThat(stats.totalMembers).isZero();
        assertThat(stats.totalPaidMembers).isZero();
        assertThat(stats.conversionRate).isZero();
    }

    @Test
    void updatingAnUnknownMemberChangesNothing() {
        Membership membership = new Membership();
        membership.addMember(new Member(1, "John Doe", MembershipStatus.BRONZE));

        membership.updateMembership(99, MembershipStatus.GOLD);

        assertThat(membership.members.get(0).membershipStatus).isEqualTo(MembershipStatus.BRONZE);
    }

    @Test
    void averageWorkoutDurations() {
        Membership membership = new Membership();
        membership.addMember(new Member(12, "John Doe", MembershipStatus.SILVER));
        membership.addMember(new Member(22, "Alex Cleeve", MembershipStatus.BRONZE));
        membership.addMember(new Member(31, "Marie Cardiff", MembershipStatus.GOLD));
        membership.addMember(new Member(37, "George Costanza", MembershipStatus.SILVER));

        membership.addWorkout(12, new Workout(11, 10, 20));
        membership.addWorkout(22, new Workout(24, 15, 35));
        membership.addWorkout(31, new Workout(32, 45, 90));
        membership.addWorkout(12, new Workout(47, 100, 155));
        membership.addWorkout(22, new Workout(56, 120, 200));
        membership.addWorkout(31, new Workout(62, 300, 400));
        membership.addWorkout(12, new Workout(78, 1000, 1010));
        membership.addWorkout(4, new Workout(80, 1010, 1045)); // no member 4: ignored

        Map<Integer, Double> averages = membership.getAverageWorkoutDurations();

        assertThat(averages.get(12)).isCloseTo(25.0, within(0.1));
        assertThat(averages.get(22)).isCloseTo(50.0, within(0.1));
        assertThat(averages.get(31)).isCloseTo(72.5, within(0.1));
        assertThat(averages).doesNotContainKey(4);
    }

    @Test
    void memberWithNoWorkoutsHasNoAverage() {
        Membership membership = new Membership();
        membership.addMember(new Member(12, "John Doe", MembershipStatus.SILVER));
        membership.addMember(new Member(37, "George Costanza", MembershipStatus.SILVER));
        membership.addWorkout(12, new Workout(11, 10, 20));

        assertThat(membership.getAverageWorkoutDurations()).containsOnlyKeys(12);
        assertThat(new Membership().getAverageWorkoutDurations()).isEmpty();
    }

    @Test
    void totalMonthlyRevenue() {
        Membership membership = new Membership();
        membership.addMember(new Member(1, "John Doe", MembershipStatus.BRONZE));
        membership.addMember(new Member(2, "Alex C", MembershipStatus.SILVER));
        membership.addMember(new Member(3, "Marie C", MembershipStatus.GOLD));
        membership.addMember(new Member(4, "Joe D", MembershipStatus.SILVER));

        // 0 + 29.99 + 49.99 + 29.99
        assertThat(membership.getTotalMonthlyRevenueCents()).isEqualTo(10997L);
        assertThat(new Membership().getTotalMonthlyRevenueCents()).isZero();
    }
}
