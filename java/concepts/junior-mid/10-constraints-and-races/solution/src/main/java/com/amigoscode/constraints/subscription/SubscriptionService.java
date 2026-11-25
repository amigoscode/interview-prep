package com.amigoscode.constraints.subscription;

import com.amigoscode.constraints.db.Violations;
import com.amigoscode.constraints.work.SlowWork;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Deliberately NOT transactional, for the same reason as SignupService. */
@Service
public class SubscriptionService {

    /** The partial unique index in schema.sql: one row per user, counting only ACTIVE rows. */
    public static final String ONE_ACTIVE_PER_USER = "subscriptions_one_active_per_user";

    private final SubscriptionRepository subscriptions;
    private final SlowWork work;

    public SubscriptionService(SubscriptionRepository subscriptions, SlowWork work) {
        this.subscriptions = subscriptions;
        this.work = work;
    }

    /** The rule: a user has at most one ACTIVE subscription. Cancelled ones are history. */
    public Subscription subscribe(long userId, Plan plan, int seats, int couponCents) {
        if (subscriptions.existsByUserIdAndStatus(userId, Status.ACTIVE)) {     // CHECK (fast path)
            throw new AlreadySubscribedException(userId);
        }
        work.run("charge the card");                                            // ...time passes...
        int priceCents = plan.priceCents() - couponCents;
        try {
            return subscriptions.save(new Subscription(userId, plan, seats, priceCents));   // INSERT
        } catch (DataIntegrityViolationException e) {
            if (Violations.isOn(e, ONE_ACTIVE_PER_USER)) {
                throw new AlreadySubscribedException(userId);
            }
            throw e;
        }
    }

    public Subscription cancel(long subscriptionId) {
        Subscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        subscription.cancel();
        return subscriptions.save(subscription);
    }
}
