package com.amigoscode.constraints.subscription;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    /** seats defaults to 1 and couponCents to 0 when they are left out. */
    public record SubscribeRequest(long userId, String plan, Integer seats, Integer couponCents) {
    }

    public record SubscriptionResponse(long id, long userId, String plan, int seats, int priceCents, String status) {

        static SubscriptionResponse of(Subscription subscription) {
            return new SubscriptionResponse(subscription.getId(), subscription.getUserId(),
                    subscription.getPlan().name(), subscription.getSeats(), subscription.getPriceCents(),
                    subscription.getStatus().name());
        }
    }

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** No validation of seats or the coupon, on purpose: Task 7 shows what the database does about it. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse subscribe(@RequestBody SubscribeRequest request) {
        int seats = request.seats() == null ? 1 : request.seats();
        int couponCents = request.couponCents() == null ? 0 : request.couponCents();
        return SubscriptionResponse.of(
                subscriptionService.subscribe(request.userId(), Plan.from(request.plan()), seats, couponCents));
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionResponse cancel(@PathVariable long id) {
        return SubscriptionResponse.of(subscriptionService.cancel(id));
    }
}
