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

    public Order get(long id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order create(OrderRequest request) {
        Order order = new Order(request.customer(), request.product(), request.quantity(), request.shippingAddress());
        order.setNotes(request.notes());
        order.setContactPhone(request.contactPhone());
        return orders.save(order);
    }

    /** PUT: the order becomes exactly what the client sent. A field that was left out is cleared. */
    public Order replace(long id, OrderRequest request) {
        Order order = get(id);
        order.setCustomer(request.customer());
        order.setProduct(request.product());
        order.setQuantity(request.quantity());
        order.setShippingAddress(request.shippingAddress());
        order.setNotes(request.notes());
        order.setContactPhone(request.contactPhone());
        order.recalculateTotal();
        return orders.save(order);
    }

    /** PATCH: only the fields the client sent change. Everything else stays as it is. */
    public Order patch(long id, OrderPatch patch) {
        Order order = get(id);
        if (patch.customer() != null) {
            order.setCustomer(patch.customer());
        }
        if (patch.product() != null) {
            order.setProduct(patch.product());
        }
        if (patch.quantity() != null) {
            order.setQuantity(patch.quantity());
        }
        if (patch.shippingAddress() != null) {
            order.setShippingAddress(patch.shippingAddress());
        }
        if (patch.notes() != null) {
            order.setNotes(patch.notes());
        }
        if (patch.contactPhone() != null) {
            order.setContactPhone(patch.contactPhone());
        }
        order.recalculateTotal();
        return orders.save(order);
    }

    public Order cancel(long id) {
        Order order = get(id);
        order.cancel();
        return orders.save(order);
    }

    public void delete(long id) {
        orders.delete(get(id));
    }
}
