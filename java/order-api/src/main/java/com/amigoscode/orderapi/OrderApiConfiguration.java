package com.amigoscode.orderapi;

import com.amigoscode.orderapi.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OrderApiConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RateLimiter orderRateLimiter(@Value("${orders.rate-limit.capacity}") int capacity,
                                 @Value("${orders.rate-limit.refill-per-second}") double refillPerSecond,
                                 Clock clock) {
        return new RateLimiter(capacity, refillPerSecond, clock);
    }
}
