package com.amigoscode.constraints;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.constraints.settings.Settings;
import com.amigoscode.constraints.settings.SettingsController;
import com.amigoscode.constraints.settings.SettingsService;
import com.amigoscode.constraints.settings.Strategy;
import com.amigoscode.constraints.subscription.AlreadySubscribedException;
import com.amigoscode.constraints.subscription.Plan;
import com.amigoscode.constraints.subscription.Subscription;
import com.amigoscode.constraints.subscription.SubscriptionController;
import com.amigoscode.constraints.subscription.SubscriptionService;
import com.amigoscode.constraints.user.EmailTakenException;
import com.amigoscode.constraints.user.SignupController;
import com.amigoscode.constraints.user.SignupService;
import com.amigoscode.constraints.user.User;
import com.amigoscode.constraints.user.UserNotFoundException;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for the HTTP contract: no Spring context, no database, the services are stubs. They
 * pass from the start and must keep passing: whatever you change in the services, a request that
 * loses a race must get a clear 409, never a 500.
 */
@Tag("baseline")
class ApiContractTest {

    private static final String SIGNUP = "{\"email\":\"ana@example.com\",\"displayName\":\"Ana\"}";
    private static final String SUBSCRIBE = "{\"userId\":7,\"plan\":\"pro\"}";

    @Test
    @DisplayName("HTTP: a signup returns 201 with the user id and the email")
    void signupReturnsCreated() throws Exception {
        User user = new User("ana@example.com", "Ana");
        ReflectionTestUtils.setField(user, "id", 42L);

        signups(() -> user).perform(post("/api/signups").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("ana@example.com"));
    }

    @Test
    @DisplayName("HTTP: an email that is taken returns 409 EMAIL_TAKEN")
    void emailTakenReturnsConflict() throws Exception {
        signups(() -> {
            throw new EmailTakenException("ana@example.com");
        }).perform(post("/api/signups").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("HTTP: a subscription returns 201 with its id, plan and status")
    void subscriptionReturnsCreated() throws Exception {
        Subscription subscription = new Subscription(7, Plan.PRO, 1, 2900);
        ReflectionTestUtils.setField(subscription, "id", 5L);

        subscriptions(() -> subscription).perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUBSCRIBE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("HTTP: a second active subscription returns 409 ALREADY_SUBSCRIBED")
    void alreadySubscribedReturnsConflict() throws Exception {
        subscriptions(() -> {
            throw new AlreadySubscribedException(7);
        }).perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(SUBSCRIBE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBSCRIBED"));
    }

    @Test
    @DisplayName("HTTP: settings of an unknown user return 404 USER_NOT_FOUND")
    void unknownUserReturnsNotFound() throws Exception {
        settings(() -> {
            throw new UserNotFoundException(7);
        }).perform(put("/api/users/7/settings"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("HTTP: an unknown settings strategy returns 400")
    void unknownStrategyReturnsBadRequest() throws Exception {
        settings(() -> new Settings(7, "light", true)).perform(put("/api/users/7/settings?strategy=hope"))
                .andExpect(status().isBadRequest());
    }

    /** Each controller wired to a service that does whatever the test says. */
    private static MockMvc signups(Supplier<User> signUp) {
        SignupService stub = new SignupService(null, null) {
            @Override
            public User signUp(String email, String displayName) {
                return signUp.get();
            }
        };
        return api(new SignupController(stub));
    }

    private static MockMvc subscriptions(Supplier<Subscription> subscribe) {
        SubscriptionService stub = new SubscriptionService(null, null) {
            @Override
            public Subscription subscribe(long userId, Plan plan, int seats, int couponCents) {
                return subscribe.get();
            }
        };
        return api(new SubscriptionController(stub));
    }

    private static MockMvc settings(Supplier<Settings> getOrCreate) {
        SettingsService stub = new SettingsService(null, null) {
            @Override
            public Settings getOrCreate(Strategy strategy, long userId) {
                return getOrCreate.get();
            }
        };
        return api(new SettingsController(stub));
    }

    private static MockMvc api(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
