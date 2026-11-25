package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.pagination.support.StubOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 2: the client does not decide how many rows one request may load. Unit tests: the real
 * controller and error handler, a stub service that writes down the size it was asked for.
 */
@Tag("task2")
class PageSizeTest {

    private final StubOrderService service = new StubOrderService();

    @Test
    @DisplayName("Page size: a request without size gets 20 orders, not every order")
    void noSizeMeansTwenty() throws Exception {
        service.api().perform(get("/api/orders")).andExpect(status().isOk());

        assertThat(service.size).as("size passed to the service for GET /api/orders").isEqualTo(20);
        assertThat(service.page).as("page passed to the service for GET /api/orders").isZero();
    }

    @Test
    @DisplayName("Page size: size=100 is the largest size that is accepted")
    void oneHundredIsAllowed() throws Exception {
        service.api().perform(get("/api/orders?size=100")).andExpect(status().isOk());

        assertThat(service.size).as("size passed to the service").isEqualTo(100);
    }

    @Test
    @DisplayName("Page size: size=101 and size=1000000 return 400 INVALID_PAGE_REQUEST, and nothing is loaded")
    void tooLargeIsRejected() throws Exception {
        for (String size : new String[] {"101", "1000000"}) {
            service.api().perform(get("/api/orders?size=" + size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
        }

        assertThat(service.calls).as("calls that reached the service").isZero();
    }

    @Test
    @DisplayName("Page size: size=0 and size=-5 return 400 INVALID_PAGE_REQUEST")
    void zeroAndNegativeAreRejected() throws Exception {
        for (String size : new String[] {"0", "-5"}) {
            service.api().perform(get("/api/orders?size=" + size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
        }

        assertThat(service.calls).as("calls that reached the service").isZero();
    }

    @Test
    @DisplayName("Page size: page=-1 returns 400 INVALID_PAGE_REQUEST")
    void negativePageIsRejected() throws Exception {
        service.api().perform(get("/api/orders?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));

        assertThat(service.calls).as("calls that reached the service").isZero();
    }

    @Test
    @DisplayName("Page size: the feed and a customer's history follow the same rules: 20 by default, 400 above 100")
    void theOtherEndpointsFollowTheSameRules() throws Exception {
        service.api().perform(get("/api/orders/feed")).andExpect(status().isOk());
        assertThat(service.size).as("size passed to the service for GET /api/orders/feed").isEqualTo(20);

        service.api().perform(get("/api/customers/7/orders")).andExpect(status().isOk());
        assertThat(service.size).as("size passed to the service for GET /api/customers/7/orders").isEqualTo(20);

        service.api().perform(get("/api/orders/feed?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
        service.api().perform(get("/api/customers/7/orders?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
    }
}
