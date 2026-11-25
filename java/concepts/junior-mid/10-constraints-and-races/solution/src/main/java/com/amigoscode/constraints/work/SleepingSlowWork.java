package com.amigoscode.constraints.work;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SleepingSlowWork implements SlowWork {

    private final long millis;

    public SleepingSlowWork(@Value("${app.work-millis:100}") long millis) {
        this.millis = millis;
    }

    @Override
    public void run(String step) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while: " + step, e);
        }
    }
}
