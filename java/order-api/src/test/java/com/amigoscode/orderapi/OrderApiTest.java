package com.amigoscode.orderapi;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.amigoscode.orderapi.OrderJson.newCustomer;
import static com.amigoscode.orderapi.OrderJson.order;
import static com.amigoscode.orderapi.OrderJson.orderWithItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stories 1 to 3 over HTTP. The rate limit is set high so it never gets in the way here. */
@SpringBootTest(properties = "orders.rate-limit.capacity=10000")
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void projectIsWiredUp() {
        assertThat(mvc).isNotNull();
    }

    String create(String body) throws Exception {
        MvcResult result = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Nested
    class Story1CreateAndGet {

        @Test
        void createsAnOrderAndReadsItBack() throws Exception {
            String customer = newCustomer();
            MvcResult created = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(order(customer)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.totalCents").value(2150))
                    .andExpect(jsonPath("$.customerId").value(customer))
                    .andExpect(jsonPath("$.restaurantId").value("rest-42"))
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andReturn();
            String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
            assertThat(created.getResponse().getHeader("Location")).endsWith("/orders/" + id);

            mvc.perform(get("/orders/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.status").value("CREATED"))
                    .andExpect(jsonPath("$.totalCents").value(2150))
                    .andExpect(jsonPath("$.items[0].name").value("Margherita"))
                    .andExpect(jsonPath("$.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.items[0].priceCents").value(950));
        }

        @Test
        void totalIsComputedByTheServerAndAnyClientTotalIsIgnored() throws Exception {
            String body = """
                    { "customerId": "%s", "restaurantId": "rest-42", "totalCents": 1,
                      "items": [ { "name": "Burger", "quantity": 3, "priceCents": 1299 } ] }
                    """.formatted(newCustomer());
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalCents").value(3897));
        }

        @Test
        void eachOrderGetsItsOwnId() throws Exception {
            String customer = newCustomer();
            assertThat(create(order(customer))).isNotEqualTo(create(order(customer)));
        }

        @Test
        void unknownOrderIs404() throws Exception {
            mvc.perform(get("/orders/{id}", "00000000-0000-0000-0000-000000000000"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Story2ListUpdateCancel {

        @Test
        void listsOnlyTheCustomersOrdersPaginated() throws Exception {
            String customer = newCustomer();
            for (int i = 0; i < 5; i++) create(order(customer));
            create(order(newCustomer())); // someone else's

            Set<String> seen = new HashSet<>();
            for (int page = 0; page < 3; page++) {
                MvcResult result = mvc.perform(get("/orders").param("customerId", customer)
                                .param("page", String.valueOf(page)).param("size", "2"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page").value(page))
                        .andExpect(jsonPath("$.size").value(2))
                        .andExpect(jsonPath("$.totalElements").value(5))
                        .andExpect(jsonPath("$.totalPages").value(3))
                        .andExpect(jsonPath("$.orders", hasSize(page < 2 ? 2 : 1)))
                        .andReturn();
                List<String> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.orders[*].id");
                seen.addAll(ids);
            }
            assertThat(seen).hasSize(5); // pages do not overlap
        }

        @Test
        void pageBeyondTheEndIsEmptyNotAnError() throws Exception {
            String customer = newCustomer();
            create(order(customer));
            mvc.perform(get("/orders").param("customerId", customer).param("page", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void customerWithNoOrdersGetsAnEmptyPage() throws Exception {
            mvc.perform(get("/orders").param("customerId", newCustomer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders", hasSize(0)))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        void listingWithoutCustomerIdOrWithBadPagingIs400() throws Exception {
            mvc.perform(get("/orders")).andExpect(status().isBadRequest());
            mvc.perform(get("/orders").param("customerId", "c").param("page", "-1")).andExpect(status().isBadRequest());
            mvc.perform(get("/orders").param("customerId", "c").param("size", "0")).andExpect(status().isBadRequest());
            mvc.perform(get("/orders").param("customerId", "c").param("size", "101")).andExpect(status().isBadRequest());
        }

        @Test
        void updatesItemsAndRecomputesTheTotal() throws Exception {
            String id = create(order(newCustomer()));
            String body = """
                    { "items": [ { "name": "Calzone", "quantity": 1, "priceCents": 1100 } ] }
                    """;
            mvc.perform(put("/orders/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.totalCents").value(1100));
            mvc.perform(get("/orders/{id}", id)).andExpect(jsonPath("$.totalCents").value(1100));
        }

        @Test
        void cancelsAnOrderAndCancellingAgainIsHarmless() throws Exception {
            String id = create(order(newCustomer()));
            mvc.perform(post("/orders/{id}/cancel", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
            mvc.perform(post("/orders/{id}/cancel", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
            mvc.perform(get("/orders/{id}", id)).andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        void updatingACancelledOrderIs409() throws Exception {
            String id = create(order(newCustomer()));
            mvc.perform(post("/orders/{id}/cancel", id)).andExpect(status().isOk());
            mvc.perform(put("/orders/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "items": [ { "name": "Calzone", "quantity": 1, "priceCents": 1100 } ] }
                                    """))
                    .andExpect(status().isConflict());
        }

        @Test
        void updatingOrCancellingAnUnknownOrderIs404() throws Exception {
            String unknown = "00000000-0000-0000-0000-000000000000";
            mvc.perform(put("/orders/{id}", unknown).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "items": [ { "name": "Calzone", "quantity": 1, "priceCents": 1100 } ] }
                                    """))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/orders/{id}/cancel", unknown)).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Story3ValidationAndErrors {

        void expectInvalid(String body, String field) throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.errors['" + field + "']").isNotEmpty());
        }

        @Test
        void emptyOrMissingItemsAreRejected() throws Exception {
            expectInvalid(orderWithItems(newCustomer(), "[]"), "items");
            expectInvalid("""
                    { "customerId": "c", "restaurantId": "r" }
                    """, "items");
        }

        @Test
        void quantityMustBeBetween1And50() throws Exception {
            expectInvalid(orderWithItems(newCustomer(), """
                    [ { "name": "Pizza", "quantity": 0, "priceCents": 900 } ]"""), "items[0].quantity");
            expectInvalid(orderWithItems(newCustomer(), """
                    [ { "name": "Pizza", "quantity": 51, "priceCents": 900 } ]"""), "items[0].quantity");
            expectInvalid(orderWithItems(newCustomer(), """
                    [ { "name": "Pizza", "priceCents": 900 } ]"""), "items[0].quantity");
        }

        @Test
        void boundaryQuantitiesAreAccepted() throws Exception {
            create(orderWithItems(newCustomer(), """
                    [ { "name": "Pizza", "quantity": 1, "priceCents": 900 },
                      { "name": "Water", "quantity": 50, "priceCents": 100 } ]"""));
        }

        @Test
        void priceMustBePositive() throws Exception {
            expectInvalid(orderWithItems(newCustomer(), """
                    [ { "name": "Pizza", "quantity": 1, "priceCents": 0 } ]"""), "items[0].priceCents");
            expectInvalid(orderWithItems(newCustomer(), """
                    [ { "name": "Pizza", "quantity": 1, "priceCents": -5 } ]"""), "items[0].priceCents");
        }

        @Test
        void namesAndIdsMustNotBeBlank() throws Exception {
            expectInvalid(orderWithItems(" ", """
                    [ { "name": "Pizza", "quantity": 1, "priceCents": 900 } ]"""), "customerId");
            expectInvalid(orderWithItems(newCustomer(), """
                    [ { "name": "", "quantity": 1, "priceCents": 900 } ]"""), "items[0].name");
            expectInvalid("""
                    { "customerId": "c", "items": [ { "name": "Pizza", "quantity": 1, "priceCents": 900 } ] }
                    """, "restaurantId");
        }

        @Test
        void updateIsValidatedToo() throws Exception {
            String id = create(order(newCustomer()));
            mvc.perform(put("/orders/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{ \"items\": [] }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.items").isNotEmpty());
        }

        @Test
        void badPagingNamesTheOffendingParameter() throws Exception {
            mvc.perform(get("/orders").param("customerId", "c").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors.size").isNotEmpty());
        }

        @Test
        void blankIdempotencyKeyIsNamedInTheErrors() throws Exception {
            mvc.perform(post("/orders").header("Idempotency-Key", "")
                            .contentType(MediaType.APPLICATION_JSON).content(order(newCustomer())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.idempotencyKey").isNotEmpty());
        }

        @Test
        void notFoundIsAProblemDetail() throws Exception {
            mvc.perform(get("/orders/{id}", "00000000-0000-0000-0000-000000000000"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Order not found"))
                    .andExpect(jsonPath("$.detail").value(endsWith("not found")))
                    .andExpect(jsonPath("$.instance").value("/orders/00000000-0000-0000-0000-000000000000"));
        }

        @Test
        void conflictIsAProblemDetail() throws Exception {
            String id = create(order(newCustomer()));
            mvc.perform(post("/orders/{id}/cancel", id));
            mvc.perform(put("/orders/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "items": [ { "name": "Calzone", "quantity": 1, "priceCents": 1100 } ] }
                                    """))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409));
        }

        @Test
        void malformedJsonAndBadIdsAreProblemDetails() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            mvc.perform(get("/orders/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        @Test
        void locationHeaderPointsAtTheNewOrder() throws Exception {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(order(newCustomer())))
                    .andExpect(header().string("Location", matchesPattern(".*/orders/[0-9a-f-]{36}")));
        }
    }
}
