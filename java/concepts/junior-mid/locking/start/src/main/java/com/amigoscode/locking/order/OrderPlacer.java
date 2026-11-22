package com.amigoscode.locking.order;

import com.amigoscode.locking.fraud.FraudCheck;
import com.amigoscode.locking.product.Product;
import com.amigoscode.locking.product.ProductNotFoundException;
import com.amigoscode.locking.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One database transaction per method. Every strategy does the same four steps: read the stock,
 * check it, run the fraud check, write the new stock and the order.
 *
 * <p>Right now all four methods have the same bug. placeNaive stays broken for ever so you can
 * always compare against it. You fix the other three, one task at a time.
 */
@Service
public class OrderPlacer {

    private final ProductRepository products;
    private final OrderRepository orders;
    private final FraudCheck fraudCheck;

    public OrderPlacer(ProductRepository products, OrderRepository orders, FraudCheck fraudCheck) {
        this.products = products;
        this.orders = orders;
        this.fraudCheck = fraudCheck;
    }

    /**
     * The bug. Do not fix this one: it uses two explicit queries, so it stays naive even after
     * Product gets a version column in Task 4.
     */
    @Transactional
    public Order placeNaive(long productId, int quantity) {
        int stock = products.findStock(productId)                 // READ
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (stock < quantity) {                                   // CHECK
            throw new SoldOutException(productId);
        }
        fraudCheck.review(productId, quantity);                   // ...time passes...
        products.writeStock(productId, stock - quantity);         // WRITE a value computed from a stale read
        return orders.save(new Order(productId, quantity));
    }

    /**
     * TODO Task 2: make the READ lock the row, so a second buyer has to wait until this
     * transaction is finished before it can read the stock.
     *
     * <p>As shipped this is the same bug as placeNaive, written the usual JPA way: load the entity,
     * change it, let Hibernate write it back.
     */
    @Transactional
    public Order placePessimistic(long productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return reserve(product, quantity);
    }

    /**
     * TODO Task 4: nothing changes in this method. Give Product a @Version, then add the retry in
     * OrderService.
     */
    @Transactional
    public Order placeOptimistic(long productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return reserve(product, quantity);
    }

    /**
     * TODO Task 6: replace the separate READ and WRITE with one UPDATE that only succeeds when
     * there is enough stock, and use the number of rows it changed to decide what happened.
     */
    @Transactional
    public Order placeAtomic(long productId, int quantity) {
        int stock = products.findStock(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        if (stock < quantity) {
            throw new SoldOutException(productId);
        }
        fraudCheck.review(productId, quantity);
        products.writeStock(productId, stock - quantity);
        return orders.save(new Order(productId, quantity));
    }

    private Order reserve(Product product, int quantity) {
        if (product.getStock() < quantity) {
            throw new SoldOutException(product.getId());
        }
        fraudCheck.review(product.getId(), quantity);
        product.setStock(product.getStock() - quantity);
        return orders.save(new Order(product.getId(), quantity));
    }
}
