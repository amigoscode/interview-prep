package com.amigoscode.fetching.customer;

import com.amigoscode.fetching.order.Order;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    private Long id;

    private String name;

    private String email;

    private String city;

    /** To-many: LAZY by default. No endpoint uses it; it is here for the "two bags" experiment in Task 9. */
    @OneToMany(mappedBy = "customer")
    private List<Order> orders = new ArrayList<>();

    protected Customer() {
    }

    public Customer(Long id, String name, String email, String city) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.city = city;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public List<Order> getOrders() {
        return orders;
    }
}
