package com.amigoscode.constraints.subscription;

import com.amigoscode.constraints.work.SlowWork;
import org.springframework.stereotype.Service;

/** Deliberately NOT transactional, for the same reason as SignupService. */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final SlowWork work;

    public SubscriptionService(SubscriptionRepository subscriptions, SlowWork work) {
        this.subscriptions = subscriptions;
        this.work = work;
    }

    /**
     * The rule: a user has at most one ACTIVE subscription. Cancelled ones are history.
     *
     * <p>TODO Task 5: the same race as the signup. Keep the check, make the database enforce the
     * rule (schema.sql), and turn its refusal into an AlreadySubscribedException.
     */
    public Subscription subscribe(long userId, Plan plan, int seats, int couponCents) {
        if (subscriptions.existsByUserIdAndStatus(userId, Status.ACTIVE)) {     // CHECK
            throw new AlreadySubscribedException(userId);
        }
        work.run("charge the card");                                            // ...time passes...
        int priceCents = plan.priceCents() - couponCents;
        return subscriptions.save(new Subscription(userId, plan, seats, priceCents));   // INSERT
    }

    public Subscription cancel(long subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        subscription.cancel();
        return subscriptions.save(subscription);
    }
}
