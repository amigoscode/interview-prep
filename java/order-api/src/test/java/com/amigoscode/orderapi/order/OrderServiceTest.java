package com.amigoscode.orderapi.order;

import com.amigoscode.orderapi.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The service without Spring or HTTP. */
class OrderServiceTest {

    final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
    final OrderService service = new OrderService(new InMemoryOrderRepository(), clock);

    static CreateOrderRequest request(String customer, int quantity) {
        return new CreateOrderRequest(customer, "rest-42", List.of(new OrderItemRequest("Pizza", quantity, 950L)));
    }

    @Test
    void getUnknownOrderThrowsNotFound() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listsNewestFirstWithPagingMaths() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(service.place(request("alice", 1)).id());
            clock.advance(Duration.ofSeconds(1));
        }
        service.place(request("bob", 1));

        OrderPage first = service.listForCustomer("alice", 0, 2);
        assertThat(first.orders()).extracting(Order::id).containsExactly(ids.get(4), ids.get(3));
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);

        OrderPage last = service.listForCustomer("alice", 2, 2);
        assertThat(last.orders()).extracting(Order::id).containsExactly(ids.get(0));

        assertThat(service.listForCustomer("alice", 3, 2).orders()).isEmpty();
        assertThat(service.listForCustomer("alice", Integer.MAX_VALUE, 100).orders()).isEmpty();
    }

    @Test
    void idempotentPlacementReplaysAndRejectsADifferentBody() {
        Placement first = service.place(request("alice", 1), "k1");
        Placement retry = service.place(request("alice", 1), "k1");

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.order().id()).isEqualTo(first.order().id());
        assertThatThrownBy(() -> service.place(request("alice", 2), "k1"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(service.listForCustomer("alice", 0, 10).totalElements()).isEqualTo(1);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void simultaneousPlacementsWithOneKeyCreateOneOrder() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Placement>> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.place(request("alice", 1), "same-key");
            }));
        }
        start.countDown();
        List<Placement> placements = new ArrayList<>();
        for (Future<Placement> r : results) placements.add(r.get());
        pool.shutdown();

        assertThat(placements).extracting(p -> p.order().id()).containsOnly(placements.getFirst().order().id());
        assertThat(placements).filteredOn(p -> !p.replayed()).hasSize(1);
        assertThat(service.listForCustomer("alice", 0, 10).totalElements()).isEqualTo(1);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void anUpdateRacingACancelNeverChangesACancelledOrder() throws Exception {
        for (int round = 0; round < 200; round++) {
            UUID id = service.place(request("alice", 1)).id();
            UpdateOrderItemsRequest update = new UpdateOrderItemsRequest(List.of(new OrderItemRequest("Salad", 3, 700L)));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> updated = pool.submit(() -> {
                start.await();
                try {
                    service.updateItems(id, update);
                    return true;
                } catch (OrderNotModifiableException e) {
                    return false;
                }
            });
            Future<Order> cancelled = pool.submit(() -> {
                start.await();
                return service.cancel(id);
            });
            start.countDown();
            boolean updateWon = updated.get();
            Order afterCancel = cancelled.get();
            pool.shutdown();

            // Either the update landed first and the cancel kept it, or the cancel won and the update was refused.
            Order finalOrder = service.get(id);
            assertThat(finalOrder.status()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(finalOrder.totalCents()).isEqualTo(updateWon ? 2100 : 950);
            assertThat(afterCancel).isEqualTo(finalOrder);
        }
    }
}
