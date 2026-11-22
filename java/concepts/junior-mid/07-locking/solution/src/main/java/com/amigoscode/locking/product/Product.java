package com.amigoscode.locking.product;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "product")
public class Product {

    @Id
    private Long id;

    private String name;

    private int stock;

    /**
     * Hibernate adds "and version = ?" to every UPDATE of this entity and bumps the value by one.
     * If another transaction changed the row first, the UPDATE matches 0 rows and Hibernate throws
     * an optimistic locking failure.
     */
    @Version
    private long version;

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

    public long getVersion() {
        return version;
    }
}
