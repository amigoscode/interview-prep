package com.amigoscode.fetching.product;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.BatchSize;

/**
 * {@code @BatchSize} on an entity: when Hibernate has to initialize one Product proxy, it loads up
 * to 50 of the Product proxies waiting in the same session with one statement.
 */
@Entity
@Table(name = "product")
@BatchSize(size = 50)
public class Product {

    @Id
    private Long id;

    private String name;

    private BigDecimal price;

    protected Product() {
    }

    public Product(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
