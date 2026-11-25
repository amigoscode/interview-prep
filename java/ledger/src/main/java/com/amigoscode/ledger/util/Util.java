package com.amigoscode.ledger.util;

import java.math.BigDecimal;

public class Util {

    public static boolean isNegativeAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0)
            return true;
        else
            throw new IllegalArgumentException("amount is negative or zero");
    }

}
