package com.amigoscode.karat.gym;

/** The result of {@link Membership#getMembershipStatistics()}. */
public class MembershipStatistics {
    public final int totalMembers;
    public final int totalPaidMembers;
    public final double conversionRate;

    public MembershipStatistics(int totalMembers, int totalPaidMembers, double conversionRate) {
        this.totalMembers = totalMembers;
        this.totalPaidMembers = totalPaidMembers;
        this.conversionRate = conversionRate;
    }
}
