package com.amigoscode.threadsafety.pricing;

import java.math.BigDecimal;

/** Where prices come from. The real one is slow, which is why PriceCache exists. */
public interface PriceSource {

    BigDecimal fetchPrice(String product);
}
