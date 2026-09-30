package com.amigoscode.orderapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static com.amigoscode.orderapi.OrderJson.newCustomer;
import static com.amigoscode.orderapi.OrderJson.order;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Story 5: 3 orders at once per customer, then one more every 2 seconds. */
@SpringBootTest(properties = {"orders.rate-limit.capacity=3", "orders.rate-limit.refill-per-second=0.5"})
@AutoConfigureMockMvc
class RateLimitApiTest {

    @TestConfiguration
    static class FrozenTime {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    String customer;

    @BeforeEach
    void freshCustomer() {
        customer = newCustomer();
    }

    ResultActions placeOrder(String customerId) throws Exception {
        return mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(order(customerId)));
    }

    @Test
    void allowsTheBurstThenReturns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) placeOrder(customer).andExpect(status().isCreated());

        placeOrder(customer)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void rejectedRequestsDoNotCreateOrders() throws Exception {
        for (int i = 0; i < 5; i++) placeOrder(customer);
        mvc.perform(get("/orders").param("customerId", customer))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void allowsAgainAfterRetryAfterHasPassed() throws Exception {
        for (int i = 0; i < 3; i++) placeOrder(customer);
        placeOrder(customer).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(1));
        placeOrder(customer)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"));

        clock.advance(Duration.ofSeconds(1));
        placeOrder(customer).andExpect(status().isCreated());
        placeOrder(customer).andExpect(status().isTooManyRequests());
    }

    @Test
    void oneCustomerCannotStarveAnother() throws Exception {
        for (int i = 0; i < 10; i++) placeOrder(customer);
        placeOrder(newCustomer()).andExpect(status().isCreated());
    }

    @Test
    void readsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) placeOrder(customer);
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/orders").param("customerId", customer)).andExpect(status().isOk());
        }
    }
}
