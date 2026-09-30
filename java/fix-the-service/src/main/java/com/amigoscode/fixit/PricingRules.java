package com.amigoscode.fixit;

import java.util.List;
import java.util.Objects;

/**
 * Every number the fee depends on, named in one place. Injected into {@link DeliveryFeeService}, so a
 * new city or a pricing experiment is a new {@code PricingRules}, not an edit to the calculation.
 */
public record PricingRules(
        Money baseFee,
        int includedKm,
        Money feePerStartedExtraKm,
        double maxDistanceKm,
        Money smallOrderThreshold,
        Money smallOrderFee,
        List<TimeWindow> peakWindows,
        Money peakSurcharge,
        Money freeDeliveryThreshold) {

    public static final PricingRules STANDARD = new PricingRules(
            Money.euros("2.49"),
            3,
            Money.euros("0.50"),
            15.0,
            Money.euros("10.00"),
            Money.euros("1.00"),
            List.of(TimeWindow.between(12, 14), TimeWindow.between(19, 21)),
            Money.euros("1.00"),
            Money.euros("30.00"));

    public PricingRules {
        Objects.requireNonNull(baseFee, "baseFee");
        Objects.requireNonNull(feePerStartedExtraKm, "feePerStartedExtraKm");
        Objects.requireNonNull(smallOrderThreshold, "smallOrderThreshold");
        Objects.requireNonNull(smallOrderFee, "smallOrderFee");
        Objects.requireNonNull(peakSurcharge, "peakSurcharge");
        Objects.requireNonNull(freeDeliveryThreshold, "freeDeliveryThreshold");
        peakWindows = List.copyOf(peakWindows);
        if (includedKm < 0 || maxDistanceKm <= 0) {
            throw new IllegalArgumentException("Distances must be positive");
        }
    }
}
