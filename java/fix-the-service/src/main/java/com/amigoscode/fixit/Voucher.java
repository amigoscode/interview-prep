package com.amigoscode.fixit;

import java.util.Objects;

/** A discount on the delivery fee. Adding a kind of voucher means adding a record, not an else-if. */
public sealed interface Voucher {

    Money applyTo(Money fee);

    record FreeDelivery() implements Voucher {
        @Override
        public Money applyTo(Money fee) {
            return Money.ZERO;
        }
    }

    record PercentOff(int percent) implements Voucher {
        public PercentOff {
            if (percent <= 0 || percent > 100) {
                throw new IllegalArgumentException("Percent must be 1..100, was " + percent);
            }
        }

        @Override
        public Money applyTo(Money fee) {
            return fee.percentOff(percent);
        }
    }

    record AmountOff(Money amount) implements Voucher {
        public AmountOff {
            Objects.requireNonNull(amount, "amount");
        }

        @Override
        public Money applyTo(Money fee) {
            return fee.minusFloorAtZero(amount);
        }
    }
}
