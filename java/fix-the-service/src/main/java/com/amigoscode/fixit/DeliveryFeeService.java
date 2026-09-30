package com.amigoscode.fixit;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Calculates the delivery fee for an order.
 *
 * <ol>
 *   <li>Distance must be more than 0 km and at most the maximum, otherwise the order is rejected.</li>
 *   <li>The base fee covers the included km. Every started km after that adds a fixed amount.</li>
 *   <li>Baskets under the small-order threshold pay a small-order fee.</li>
 *   <li>Peak hours add a surcharge. Peak windows are start included, end excluded, in the clock's zone.</li>
 *   <li>Baskets at or above the free-delivery threshold pay nothing.</li>
 *   <li>A voucher, if the code is known, is applied once. Unknown codes are logged and ignored.</li>
 * </ol>
 *
 * The numbers live in {@link PricingRules}, the vouchers in a {@link VoucherCatalog}, and the time
 * comes from an injected {@link Clock}, so every rule can be tested without waiting for lunchtime.
 */
public final class DeliveryFeeService {

    private static final Logger LOG = System.getLogger(DeliveryFeeService.class.getName());

    private final PricingRules rules;
    private final VoucherCatalog vouchers;
    private final Clock clock;

    public DeliveryFeeService(PricingRules rules, VoucherCatalog vouchers, Clock clock) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.vouchers = Objects.requireNonNull(vouchers, "vouchers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Money calculateFee(DeliveryRequest request) {
        Objects.requireNonNull(request, "request");
        requireDeliverable(request.distanceKm());

        if (qualifiesForFreeDelivery(request.basketValue())) {
            LOG.log(Level.DEBUG, "Free delivery for basket {0}", request.basketValue());
            return Money.ZERO;
        }

        Money fee = rules.baseFee()
                .plus(extraDistanceFee(request.distanceKm()))
                .plus(smallOrderFee(request.basketValue()))
                .plus(peakSurcharge(LocalTime.now(clock)));

        Money finalFee = applyVoucher(fee, request.voucher());
        LOG.log(Level.DEBUG, "Delivery fee {0} (before voucher {1}) for {2}", finalFee, fee, request);
        return finalFee;
    }

    private void requireDeliverable(double distanceKm) {
        if (!(distanceKm > 0) || distanceKm > rules.maxDistanceKm()) {
            throw new IllegalArgumentException(
                    "Distance must be in (0, " + rules.maxDistanceKm() + "] km, was " + distanceKm);
        }
    }

    private boolean qualifiesForFreeDelivery(Money basketValue) {
        return !basketValue.isLessThan(rules.freeDeliveryThreshold());
    }

    private Money extraDistanceFee(double distanceKm) {
        long startedExtraKm = (long) Math.ceil(Math.max(0, distanceKm - rules.includedKm()));
        return rules.feePerStartedExtraKm().times(startedExtraKm);
    }

    private Money smallOrderFee(Money basketValue) {
        return basketValue.isLessThan(rules.smallOrderThreshold()) ? rules.smallOrderFee() : Money.ZERO;
    }

    private Money peakSurcharge(LocalTime now) {
        boolean peak = rules.peakWindows().stream().anyMatch(window -> window.contains(now));
        return peak ? rules.peakSurcharge() : Money.ZERO;
    }

    private Money applyVoucher(Money fee, Optional<String> code) {
        if (code.isEmpty()) {
            return fee;
        }
        Optional<Voucher> voucher = vouchers.find(code.get());
        if (voucher.isEmpty()) {
            LOG.log(Level.INFO, "Ignoring unknown voucher code {0}", code.get());
            return fee;
        }
        return voucher.get().applyTo(fee);
    }
}
