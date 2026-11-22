package com.amigoscode.fetching.order;

import com.amigoscode.fetching.customer.Customer;
import com.amigoscode.fetching.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "placed_at")
    private Instant placedAt;

    // TODO Task 2: this association has no fetch type, so it gets the default for @ManyToOne.
    //      Find out what the default is (./inspect), then make it LAZY.
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // No fetch type here either, so this one gets the default for @OneToMany.
    @OneToMany(mappedBy = "order")
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
