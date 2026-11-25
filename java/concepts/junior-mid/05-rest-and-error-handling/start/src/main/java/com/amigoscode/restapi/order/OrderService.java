package com.amigoscode.restapi.order;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;

    public OrderService(OrderRepository orders) {
        this.orders = orders;
    }

    public List<Order> list(OrderStatus status) {
        return status == null ? orders.findAllByOrderById() : orders.findByStatusOrderById(status);
    }

    // TODO Task 4: what does orElseThrow() throw when there is no order 999? What should the client get?
    public Order get(long id) {
        return orders.findById(id).orElseThrow();
    }

    public Order create(OrderRequest request) {
        Order order = new Order(request.customer(), request.product(), request.quantity(), request.shippingAddress());
        order.setNotes(request.notes());
        order.setContactPhone(request.contactPhone());
        return orders.save(order);
    }

    // TODO Task 4: there is no order 999. What does this method do with PUT /api/orders/999?
    // TODO Task 7: the order that is saved is the object the client sent. All of it.
    public Order update(long id, Order order) {
        order.setId(id);
        if (order.getTotal() == null) {
            order.recalculateTotal();
        }
        return orders.save(order);
    }

    // TODO Task 8: there is no way to change one field of an order.

    public Order cancel(long id) {
        Order order = get(id);
        order.cancel();
        return orders.save(order);
    }

    public void delete(long id) {
        orders.delete(get(id));
    }
}
