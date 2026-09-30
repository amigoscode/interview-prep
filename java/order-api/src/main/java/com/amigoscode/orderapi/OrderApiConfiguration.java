package com.amigoscode.orderapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OrderApiConfiguration {

    /** Inject this wherever you need "now", so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
