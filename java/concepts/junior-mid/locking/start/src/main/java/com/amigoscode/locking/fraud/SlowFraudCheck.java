package com.amigoscode.locking.fraud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlowFraudCheck implements FraudCheck {

    private final long millis;

    public SlowFraudCheck(@Value("${shop.fraud-check-millis:100}") long millis) {
        this.millis = millis;
    }

    @Override
    public void review(long productId, int quantity) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fraud check interrupted", e);
        }
    }
}
