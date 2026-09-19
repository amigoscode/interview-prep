package com.amigoscode.lb;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadBalancerTest {

    @Nested
    class Story1Register {

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void capacityMustBePositive(int capacity) {
            assertThatThrownBy(() -> new LoadBalancer(capacity)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void registersUpToCapacity() {
            LoadBalancer lb = new LoadBalancer(2);
            assertThat(lb.register("10.0.0.1")).isTrue();
            assertThat(lb.register("10.0.0.2")).isTrue();
            assertThat(lb.register("10.0.0.3")).isFalse();
            assertThat(lb.size()).isEqualTo(2);
        }

        @Test
        void rejectsDuplicates() {
            LoadBalancer lb = new LoadBalancer(10);
            assertThat(lb.register("10.0.0.1")).isTrue();
            assertThat(lb.register("10.0.0.1")).isFalse();
            assertThat(lb.size()).isEqualTo(1);
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> new LoadBalancer(10).register(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Story2Unregister {

        @Test
        void unregisterReportsWhetherItWasPresent() {
            LoadBalancer lb = new LoadBalancer(10);
            assertThat(lb.unregister("10.0.0.1")).isFalse();
            lb.register("10.0.0.1");
            assertThat(lb.unregister("10.0.0.1")).isTrue();
            assertThat(lb.unregister("10.0.0.1")).isFalse();
        }

        @Test
        void unregisterFreesCapacity() {
            LoadBalancer lb = new LoadBalancer(1);
            lb.register("10.0.0.1");
            lb.unregister("10.0.0.1");
            assertThat(lb.register("10.0.0.2")).isTrue();
        }
    }

    @Nested
    class Story3Get {

        @Test
        void throwsWhenNothingIsRegistered() {
            assertThatThrownBy(() -> new LoadBalancer(10).get()).isInstanceOf(NoInstancesException.class);
        }

        @Test
        void returnsOnlyRegisteredAddresses() {
            LoadBalancer lb = new LoadBalancer(10);
            List<String> registered = List.of("a", "b", "c");
            registered.forEach(lb::register);
            for (int i = 0; i < 100; i++) assertThat(lb.get()).isIn(registered);
        }

        @Test
        void randomEventuallyReturnsEveryAddress() {
            LoadBalancer lb = new LoadBalancer(10, new RandomSelection());
            lb.register("a"); lb.register("b"); lb.register("c");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 1_000; i++) seen.add(lb.get());
            assertThat(seen).containsExactlyInAnyOrder("a", "b", "c");
        }
    }

    @Nested
    class Story4Strategy {

        @Test
        void roundRobinCyclesInRegistrationOrderAndWraps() {
            LoadBalancer lb = new LoadBalancer(10, new RoundRobinSelection());
            lb.register("a"); lb.register("b"); lb.register("c");
            assertThat(List.of(lb.get(), lb.get(), lb.get(), lb.get(), lb.get()))
                .containsExactly("a", "b", "c", "a", "b");
        }

        @Test
        void roundRobinAdaptsWhenAnAddressLeaves() {
            LoadBalancer lb = new LoadBalancer(10, new RoundRobinSelection());
            lb.register("a"); lb.register("b"); lb.register("c");
            lb.get(); // a
            lb.unregister("b");
            assertThat(List.of(lb.get(), lb.get(), lb.get())).containsExactly("c", "a", "c");
        }
    }
}
