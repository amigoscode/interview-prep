package com.amigoscode.restapi.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Only the five methods the service needs. Keep it as it is: the unit tests replace it with an
 * in-memory version written by hand.
 */
public interface OrderRepository extends Repository<Order, Long> {

    List<Order> findAllByOrderById();

    List<Order> findByStatusOrderById(OrderStatus status);

    Optional<Order> findById(Long id);

    Order save(Order order);

    void delete(Order order);
}
