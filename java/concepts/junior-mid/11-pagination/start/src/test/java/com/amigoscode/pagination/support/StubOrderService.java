package com.amigoscode.pagination.support;

import com.amigoscode.pagination.ApiExceptionHandler;
import com.amigoscode.pagination.order.Cursor;
import com.amigoscode.pagination.order.FeedPage;
import com.amigoscode.pagination.order.OffsetPage;
import com.amigoscode.pagination.order.OrderController;
import com.amigoscode.pagination.order.OrderResponse;
import com.amigoscode.pagination.order.OrderService;
import java.time.Instant;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * An OrderService that never touches a database: it writes down what the controller asked for and
 * returns one fixed order. Written by hand, no mocking library.
 */
public final class StubOrderService extends OrderService {

    public static final OrderResponse AN_ORDER =
            new OrderResponse(42, 7, "PENDING", 1999, Instant.parse("2025-11-25T10:00:00Z"));

    /** What the controller passed on in the last call. null means: that argument was not part of the call. */
    public Integer page;
    public Integer size;
    public Long customerId;
    public Cursor after;
    public int calls;

    public StubOrderService() {
        super(null);
    }

    /** The real controller and the real error handler, wired to this stub. */
    public MockMvc api() {
        return MockMvcBuilders.standaloneSetup(new OrderController(this))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Override
    public OffsetPage page(int page, int size) {
        calls++;
        this.page = page;
        this.size = size;
        return new OffsetPage(List.of(AN_ORDER), page, size, true);
    }

    @Override
    public OffsetPage history(long customerId, int page, int size) {
        calls++;
        this.customerId = customerId;
        this.page = page;
        this.size = size;
        return new OffsetPage(List.of(AN_ORDER), page, size, false);
    }

    @Override
    public FeedPage feed(Cursor after, int size) {
        calls++;
        this.after = after;
        this.size = size;
        return new FeedPage(List.of(AN_ORDER), "next-cursor", true);
    }
}
