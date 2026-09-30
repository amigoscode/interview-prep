package com.amigoscode.fixit;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Looks up a voucher by code. In production this is a database or a promotions service. */
@FunctionalInterface
public interface VoucherCatalog {

    Optional<Voucher> find(String code);

    /** An in-memory catalog. Codes are matched case-insensitively. */
    static VoucherCatalog of(Map<String, Voucher> vouchers) {
        Map<String, Voucher> byCode = vouchers.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> normalise(e.getKey()), Map.Entry::getValue));
        return code -> Optional.ofNullable(byCode.get(normalise(code)));
    }

    static VoucherCatalog standard() {
        return of(Map.of(
                "FREEDEL", new Voucher.FreeDelivery(),
                "SAVE15", new Voucher.PercentOff(15),
                "EURO1", new Voucher.AmountOff(Money.euros("1.00"))));
    }

    private static String normalise(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
