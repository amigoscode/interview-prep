package com.amigoscode.orderapi;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void projectIsWiredUp() {
        assertThat(mvc).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void createsAnOrderAndReadsItBack() throws Exception {
        String body = """
                {
                  "customerId": "customer-1",
                  "restaurantId": "rest-42",
                  "items": [
                    { "name": "Margherita", "quantity": 2, "priceCents": 950 },
                    { "name": "Cola", "quantity": 1, "priceCents": 250 }
                  ]
                }
                """;
        MvcResult created = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalCents").value(2150))
                .andReturn();
        String location = created.getResponse().getHeader("Location");
        assertThat(location).contains("/orders/");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalCents").value(2150));

        mvc.perform(get("/orders/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
