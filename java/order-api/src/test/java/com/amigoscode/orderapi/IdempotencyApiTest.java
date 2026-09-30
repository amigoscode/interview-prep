package com.amigoscode.orderapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.amigoscode.orderapi.OrderJson.newCustomer;
import static com.amigoscode.orderapi.OrderJson.order;
import static com.amigoscode.orderapi.OrderJson.orderWithItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Story 4: the app retries POST /orders after a timeout. */
@SpringBootTest(properties = "orders.rate-limit.capacity=10000")
@AutoConfigureMockMvc
class IdempotencyApiTest {

    @Autowired
    MockMvc mvc;

    MockHttpServletResponse send(String key, String body) throws Exception {
        var request = post("/orders")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) request.header("Idempotency-Key", key);
        return mvc.perform(request).andReturn().getResponse();
    }

    static String id(MockHttpServletResponse response) throws Exception {
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    int ordersFor(String customer) throws Exception {
        String json = mvc.perform(get("/orders").param("customerId", customer)).andReturn().getResponse().getContentAsString();
        return JsonPath.<Integer>read(json, "$.totalElements");
    }

    @Test
    void sameKeyAndSameBodyReturnsTheOriginalOrder() throws Exception {
        String customer = newCustomer();
        String key = UUID.randomUUID().toString();

        MockHttpServletResponse first = send(key, order(customer));
        MockHttpServletResponse retry = send(key, order(customer));

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(id(retry)).isEqualTo(id(first));
        assertThat(retry.getHeader("Location")).isEqualTo(first.getHeader("Location"));
        assertThat(first.getHeader("Idempotent-Replayed")).isNull();
        assertThat(retry.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(ordersFor(customer)).isEqualTo(1);
    }

    @Test
    void sameKeyWithADifferentBodyIs422() throws Exception {
        String customer = newCustomer();
        String key = UUID.randomUUID().toString();
        send(key, order(customer));

        MockHttpServletResponse different = send(key, orderWithItems(customer, """
                [ { "name": "Sushi", "quantity": 1, "priceCents": 1800 } ]"""));

        assertThat(different.getStatus()).isEqualTo(422);
        assertThat(different.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(ordersFor(customer)).isEqualTo(1);
    }

    @Test
    void differentKeysCreateDifferentOrders() throws Exception {
        String customer = newCustomer();
        assertThat(id(send("key-a", order(customer)))).isNotEqualTo(id(send("key-b", order(customer))));
        assertThat(ordersFor(customer)).isEqualTo(2);
    }

    @Test
    void keysAreScopedToTheCustomer() throws Exception {
        String key = UUID.randomUUID().toString();
        MockHttpServletResponse alice = send(key, order(newCustomer()));
        MockHttpServletResponse bob = send(key, order(newCustomer()));
        assertThat(bob.getStatus()).isEqualTo(201);
        assertThat(id(bob)).isNotEqualTo(id(alice));
    }

    @Test
    void withoutAKeyEveryPostCreatesANewOrder() throws Exception {
        String customer = newCustomer();
        assertThat(id(send(null, order(customer)))).isNotEqualTo(id(send(null, order(customer))));
    }

    @Test
    void blankOrOverlongKeyIs400() throws Exception {
        assertThat(send("", order(newCustomer())).getStatus()).isEqualTo(400);
        assertThat(send("k".repeat(256), order(newCustomer())).getStatus()).isEqualTo(400);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void simultaneousRequestsWithTheSameKeyCreateExactlyOneOrder() throws Exception {
        String customer = newCustomer();
        String key = UUID.randomUUID().toString();
        int threads = 16;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return send(key, order(customer));
            }));
        }
        start.countDown();

        List<String> ids = new ArrayList<>();
        for (Future<MockHttpServletResponse> result : results) {
            MockHttpServletResponse response = result.get();
            assertThat(response.getStatus()).isEqualTo(201);
            ids.add(id(response));
        }
        pool.shutdown();

        assertThat(ids).containsOnly(ids.getFirst());
        assertThat(ordersFor(customer)).isEqualTo(1);
    }

    @Test
    void replayAfterCancelReturnsTheSameOrderNotANewOne() throws Exception {
        String customer = newCustomer();
        String key = UUID.randomUUID().toString();
        String id = id(send(key, order(customer)));
        mvc.perform(post("/orders/{id}/cancel", id)).andExpect(status().isOk());

        mvc.perform(post("/orders")
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(order(customer)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
