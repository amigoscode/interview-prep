package com.amigoscode.threadsafety.pricing;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    public record Price(String product, BigDecimal price) {
    }

    private final PriceCache cache;
    private final LockedPriceCache lockedCache;

    public PriceController(PriceCache cache, LockedPriceCache lockedCache) {
        this.cache = cache;
        this.lockedCache = lockedCache;
    }

    @GetMapping("/{product}")
    public Price price(@PathVariable String product) {
        return new Price(product, cache.priceOf(product));
    }

    @GetMapping("/locked/{product}")
    public Price lockedPrice(@PathVariable String product) {
        return new Price(product, lockedCache.priceOf(product));
    }
}
