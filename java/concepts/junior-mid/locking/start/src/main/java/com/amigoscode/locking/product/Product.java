package com.amigoscode.locking.product;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

    @Id
    private Long id;

    private String name;

    private int stock;

    // TODO Task 4: the product table already has a "version" column (see schema.sql).
    //      Map it here so Hibernate checks it on every UPDATE.

    protected Product() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
