package com.amigoscode.threadsafety.sales;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesStats stats;
    private final WrongLockSalesStats wrongLockStats;

    public SalesController(SalesStats stats, WrongLockSalesStats wrongLockStats) {
        this.stats = stats;
        this.wrongLockStats = wrongLockStats;
    }

    @PostMapping
    public void sale(@RequestParam long amountCents) {
        stats.record(amountCents);
    }

    @GetMapping("/summary")
    public SalesSummary summary() {
        return stats.summary();
    }

    @PostMapping("/wrong-lock")
    public void saleWrongLock(@RequestParam long amountCents) {
        wrongLockStats.record(amountCents);
    }

    @GetMapping("/wrong-lock/summary")
    public SalesSummary summaryWrongLock() {
        return wrongLockStats.summary();
    }
}
