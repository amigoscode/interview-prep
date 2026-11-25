package com.amigoscode.constraints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.amigoscode.constraints.subscription.Plan;
import com.amigoscode.constraints.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Task 7. The application validates none of this, and it stays that way: these tests are about the
 * database. Each one tries the bad row twice, with plain SQL and through the service.
 */
@Tag("task7")
class BadDataIT extends IntegrationTest {

    private static final long MISSING_USER = 999_999;
    private static final String INSERT_SUBSCRIPTION =
            "insert into subscriptions (user_id, plan, seats, price_cents, status) values (?, 'PRO', ?, ?, 'CANCELLED')";

    @Test
    @DisplayName("NOT NULL: a user without an email is refused by the database, and a signup without one stores nothing")
    void aUserNeedsAnEmail() {
        assertThatThrownBy(() -> jdbc.update("insert into users (email, display_name) values (null, 'No Email')"))
                .as("a plain SQL insert with email = NULL")
                .isInstanceOf(DataIntegrityViolationException.class);

        catchThrowable(() -> signupService.signUp(null, "No Email"));

        assertThat(userRows()).as("rows in users").isEqualTo(0);
    }

    @Test
    @DisplayName("CHECK: a subscription with -3 seats is refused by the database, and subscribing with -3 seats stores nothing")
    void seatsMustBePositive() {
        long user = insertUser("bo@example.com");

        assertThatThrownBy(() -> jdbc.update(INSERT_SUBSCRIPTION, user, -3, 2900))
                .as("a plain SQL insert with seats = -3")
                .isInstanceOf(DataIntegrityViolationException.class);

        catchThrowable(() -> subscriptionService.subscribe(user, Plan.PRO, -3, 0));

        assertThat(subscriptionRows()).as("rows in subscriptions").isEqualTo(0);
    }

    @Test
    @DisplayName("CHECK: a negative price is refused by the database, and a coupon bigger than the price stores nothing")
    void thePriceMustNotBeNegative() {
        long user = insertUser("cy@example.com");

        assertThatThrownBy(() -> jdbc.update(INSERT_SUBSCRIPTION, user, 1, -4100))
                .as("a plain SQL insert with price_cents = -4100")
                .isInstanceOf(DataIntegrityViolationException.class);

        catchThrowable(() -> subscriptionService.subscribe(user, Plan.BASIC, 1, 5000));

        assertThat(subscriptionRows()).as("rows in subscriptions").isEqualTo(0);
    }

    @Test
    @DisplayName("FOREIGN KEY: a subscription for a user that does not exist is refused by the database, and subscribing one stores nothing")
    void aSubscriptionNeedsAUser() {
        assertThatThrownBy(() -> jdbc.update(INSERT_SUBSCRIPTION, MISSING_USER, 1, 2900))
                .as("a plain SQL insert with a user_id that is not in users")
                .isInstanceOf(DataIntegrityViolationException.class);

        catchThrowable(() -> subscriptionService.subscribe(MISSING_USER, Plan.PRO, 1, 0));

        assertThat(subscriptionRows()).as("rows in subscriptions").isEqualTo(0);
    }
}
