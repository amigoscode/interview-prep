package com.amigoscode.orderapi.order;

import com.amigoscode.orderapi.ratelimit.RateLimitExceededException;
import com.amigoscode.orderapi.ratelimit.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final OrderService orders;
    private final RateLimiter rateLimiter;

    public OrderController(OrderService orders, RateLimiter rateLimiter) {
        this.orders = orders;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Stories 1, 4, 5. A replay answers exactly like the original (201 and the same Location) so a
     * retrying client needs no special case; the {@code Idempotent-Replayed} header tells logs and
     * curious clients which one it was.
     */
    @PostMapping
    public ResponseEntity<Order> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(min = 1, max = 255) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        RateLimiter.Decision decision = rateLimiter.tryAcquire(request.customerId());
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }

        Placement placement = idempotencyKey == null
                ? new Placement(orders.place(request), false)
                : orders.place(request, idempotencyKey);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(placement.order().id())
                .toUri();
        ResponseEntity.BodyBuilder response = ResponseEntity.created(location);
        if (placement.replayed()) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(placement.order());
    }

    /** Story 1. */
    @GetMapping("/{id}")
    public Order get(@PathVariable UUID id) {
        return orders.get(id);
    }

    /** Story 2. */
    @GetMapping
    public OrderPage list(@RequestParam @NotBlank String customerId,
                          @RequestParam(defaultValue = "0") @Min(0) int page,
                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.listForCustomer(customerId, page, size);
    }

    /** Story 2. PUT because the body replaces the whole item list. */
    @PutMapping("/{id}")
    public Order updateItems(@PathVariable UUID id, @Valid @RequestBody UpdateOrderItemsRequest request) {
        return orders.updateItems(id, request);
    }

    /**
     * Story 2. Cancel is a state transition, not a deletion: the order stays readable (the customer,
     * support and the restaurant all still need it), so it is a POST to a sub-resource rather than
     * DELETE. Cancelling a cancelled order returns it unchanged, so retries are safe.
     */
    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable UUID id) {
        return orders.cancel(id);
    }
}
