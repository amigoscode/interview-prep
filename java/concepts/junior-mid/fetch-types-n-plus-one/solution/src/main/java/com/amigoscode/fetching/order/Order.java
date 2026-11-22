package com.amigoscode.fetching.order;

import com.amigoscode.fetching.customer.Customer;
import com.amigoscode.fetching.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "placed_at")
    private Instant placedAt;

    /**
     * To-one associations are EAGER unless you say otherwise. LAZY means: put a proxy here, and load
     * the customer only when somebody calls a method on it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /**
     * To-many associations are LAZY by default. {@code @BatchSize}: when the items of one order are
     * needed, load the items of up to 50 orders waiting in the same session with one statement.
     */
    @OneToMany(mappedBy = "order")
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(Long id, OrderStatus status, Instant placedAt, Customer customer) {
        this.id = id;
        this.status = status;
        this.placedAt = placedAt;
        this.customer = customer;
    }

    public Order addItem(Product product, int quantity) {
        items.add(new OrderItem(this, product, quantity));
        return this;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
