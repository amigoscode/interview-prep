package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.constraints.subscription.AlreadySubscribedException;
import com.amigoscode.constraints.subscription.Plan;
import com.amigoscode.constraints.subscription.Subscription;
import com.amigoscode.constraints.support.IntegrationTest;
import com.amigoscode.constraints.support.Race;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

@Tag("task5")
class OneActiveSubscriptionIT extends IntegrationTest {

    private static final String INSERT_ACTIVE =
            "insert into subscriptions (user_id, plan, seats, price_cents, status) values (?, 'PRO', 1, 2900, 'ACTIVE')";

    @Test
    @DisplayName("Partial: the database refuses a second ACTIVE subscription for the same user, even from code that skips the service")
    void theDatabaseRefusesASecondActiveRow() {
        long user = insertUser("sam@example.com");
        jdbc.update(INSERT_ACTIVE, user);

        assertThatThrownBy(() -> jdbc.update(INSERT_ACTIVE, user))
                .as("a plain SQL insert of a second ACTIVE row")
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(subscriptionRows(user, "ACTIVE")).as("ACTIVE rows").isEqualTo(1);
    }

    @Test
    @DisplayName("Partial: one user subscribes twice at the same moment: one ACTIVE subscription, the other request is told the user is already subscribed")
    void onlyOneOfTwoRequestsSubscribes() {
        long user = insertUser("sam@example.com");
        work.everyonePassesTheCheckBeforeAnyoneInserts(2);

        Race.Result<Subscription> result = Race.run(2, () -> subscriptionService.subscribe(user, Plan.PRO, 1, 0));

        assertThat(result.failedWithAnythingBut(AlreadySubscribedException.class)).as("errors that would be a 500").isEmpty();
        assertThat(result.succeeded()).as("subscriptions started").hasSize(1);
        assertThat(result.failedWith(AlreadySubscribedException.class)).as("requests told already subscribed").isEqualTo(1);
        assertThat(subscriptionRows(user, "ACTIVE")).as("ACTIVE rows").isEqualTo(1);
    }

    @Test
    @DisplayName("Partial: cancelled subscriptions may repeat: subscribe, cancel, subscribe, cancel, subscribe leaves 2 CANCELLED rows and 1 ACTIVE row")
    void cancelledRowsMayRepeat() {
        long user = insertUser("sam@example.com");

        subscriptionService.cancel(subscriptionService.subscribe(user, Plan.BASIC, 1, 0).getId());
        subscriptionService.cancel(subscriptionService.subscribe(user, Plan.PRO, 1, 0).getId());
        subscriptionService.subscribe(user, Plan.PRO, 1, 0);

        assertThat(subscriptionRows(user, "CANCELLED")).as("CANCELLED rows").isEqualTo(2);
        assertThat(subscriptionRows(user, "ACTIVE")).as("ACTIVE rows").isEqualTo(1);
    }

    @Test
    @DisplayName("Partial: the rule is per user: two different users are both ACTIVE at the same time")
    void twoUsersCanBothBeActive() {
        long sam = insertUser("sam@example.com");
        long lee = insertUser("lee@example.com");

        subscriptionService.subscribe(sam, Plan.PRO, 1, 0);
        subscriptionService.subscribe(lee, Plan.PRO, 1, 0);

        assertThat(subscriptionRows(sam, "ACTIVE") + subscriptionRows(lee, "ACTIVE")).as("ACTIVE rows").isEqualTo(2);
    }
}
