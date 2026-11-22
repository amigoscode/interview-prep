package com.amigoscode.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.locking.order.Order;
import com.amigoscode.locking.order.OrderConflictException;
import com.amigoscode.locking.order.OrderPlacer;
import com.amigoscode.locking.order.OrderService;
import com.amigoscode.locking.order.SoldOutException;
import com.amigoscode.locking.order.Strategy;
import com.amigoscode.locking.product.Product;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Unit tests: no Spring, no database. OrderPlacer is replaced by a scripted stub, so only the retry
 * loop in OrderService is under test and the whole class runs in milliseconds.
 */
@Tag("task4")
class OrderServiceRetryTest {

    @Test
    @DisplayName("Retry: when the version check fails, the order is tried again in a new transaction and succeeds")
    void retriesWhenTheVersionCheckFails() {
        Order order = new Order(1, 1);
        ScriptedPlacer placer = new ScriptedPlacer(versionConflict(), order);

        Order placed = new OrderService(placer).placeOrder(Strategy.OPTIMISTIC, 1, 1);

        assertThat(placed).isSameAs(order);
        assertThat(placer.attempts).as("attempts").isEqualTo(2);
    }

    @Test
    @DisplayName("Retry: after 3 failed attempts it stops and throws OrderConflictException (a 409, not a 500)")
    void givesUpAfterThreeAttempts() {
        ScriptedPlacer placer = new ScriptedPlacer(versionConflict());

        assertThatThrownBy(() -> new OrderService(placer).placeOrder(Strategy.OPTIMISTIC, 1, 1))
                .isInstanceOf(OrderConflictException.class);

        assertThat(placer.attempts).as("attempts").isEqualTo(3);
    }

    @Test
    @DisplayName("Retry: a sold-out product is not retried, because trying again cannot change the answer")
    void doesNotRetryWhenSoldOut() {
        ScriptedPlacer placer = new ScriptedPlacer(new SoldOutException(1));

        assertThatThrownBy(() -> new OrderService(placer).placeOrder(Strategy.OPTIMISTIC, 1, 1))
                .isInstanceOf(SoldOutException.class);

        assertThat(placer.attempts).as("attempts").isEqualTo(1);
    }

    private static ObjectOptimisticLockingFailureException versionConflict() {
        return new ObjectOptimisticLockingFailureException(Product.class, 1L);
    }

    /**
     * Plays back a script: each call to placeOptimistic returns the next Order or throws the next
     * exception. The last entry repeats for ever.
     */
    private static final class ScriptedPlacer extends OrderPlacer {

        private final Deque<Object> script;
        private int attempts;

        ScriptedPlacer(Object... outcomes) {
            super(null, null, null);
            this.script = new ArrayDeque<>(List.of(outcomes));
        }

        @Override
        public Order placeOptimistic(long productId, int quantity) {
            attempts++;
            Object outcome = script.size() > 1 ? script.poll() : script.peek();
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            return (Order) outcome;
        }
    }
}
