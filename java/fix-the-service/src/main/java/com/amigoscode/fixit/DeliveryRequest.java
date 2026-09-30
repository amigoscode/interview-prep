package com.amigoscode.fixit;

import java.util.Objects;
import java.util.Optional;

/** What the fee depends on, apart from the time of day. {@code voucherCode} may be null. */
public record DeliveryRequest(double distanceKm, Money basketValue, String voucherCode) {

    public DeliveryRequest {
        Objects.requireNonNull(basketValue, "basketValue");
        if (basketValue.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Basket value cannot be negative: " + basketValue);
        }
    }

    public static DeliveryRequest of(double distanceKm, Money basketValue) {
        return new DeliveryRequest(distanceKm, basketValue, null);
    }

    public DeliveryRequest withVoucher(String code) {
        return new DeliveryRequest(distanceKm, basketValue, code);
    }

    public Optional<String> voucher() {
        return Optional.ofNullable(voucherCode).map(String::strip).filter(code -> !code.isEmpty());
    }
}
