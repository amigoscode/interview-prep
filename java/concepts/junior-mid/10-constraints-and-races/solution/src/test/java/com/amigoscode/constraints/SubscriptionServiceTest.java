package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.constraints.subscription.AlreadySubscribedException;
import com.amigoscode.constraints.subscription.Plan;
import com.amigoscode.constraints.subscription.SubscriptionRepository;
import com.amigoscode.constraints.subscription.SubscriptionService;
import com.amigoscode.constraints.support.Stub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Unit tests: no Spring, no database. SubscriptionRepository is a stub whose save is refused by a named constraint. */
@Tag("task5")
class SubscriptionServiceTest {

    @Test
    @DisplayName("Translate: when the insert is refused by subscriptions_one_active_per_user, it fails with AlreadySubscribedException (a 409, not a 500)")
    void aViolationOfTheOneActiveIndexMeansAlreadySubscribed() {
        SubscriptionService service = serviceWhoseInsertViolates("subscriptions_one_active_per_user");

        assertThatThrownBy(() -> service.subscribe(7, Plan.PRO, 1, 0))
                .isInstanceOf(AlreadySubscribedException.class);
    }

    @Test
    @DisplayName("Translate: a violation of any other constraint is not reported as \"already subscribed\"")
    void aViolationOfAnotherConstraintIsNotAlreadySubscribed() {
        SubscriptionService service = serviceWhoseInsertViolates("subscriptions_seats_check");

        assertThatThrownBy(() -> service.subscribe(7, Plan.PRO, -3, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static SubscriptionService serviceWhoseInsertViolates(String constraintName) {
        SubscriptionRepository subscriptions = Stub.repository(SubscriptionRepository.class, subscription -> {
            throw SignupServiceTest.refusedBy(constraintName);
        });
        return new SubscriptionService(subscriptions, step -> { });
    }
}
