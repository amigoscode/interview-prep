package com.amigoscode.locking.order;

import com.amigoscode.locking.fraud.FraudCheck;
import com.amigoscode.locking.product.Product;
import com.amigoscode.locking.product.ProductNotFoundException;
import com.amigoscode.locking.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One database transaction per method. Every strategy does the same four steps: read the stock,
 * check it, run the fraud check, write the new stock and the order. They differ only in how they
 * stop a second buyer from acting on the same stock value.
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
     * The bug. Kept as it is so you can always compare against it: it uses two explicit queries, so
     * it stays naive even after Product gets a version column.
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
     * Pessimistic: SELECT ... FOR UPDATE locks the row at the READ. A second buyer waits at its own
     * READ until this transaction commits, then sees the new stock.
     */
    @Transactional
    public Order placePessimistic(long productId, int quantity) {
        Product product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return reserve(product, quantity);
    }

    /**
     * Optimistic: nobody waits. Product has a @Version, so the UPDATE at commit only succeeds if
     * the row is still at the version this transaction read. The loser gets an
     * ObjectOptimisticLockingFailureException at commit, and OrderService retries it in a new
     * transaction.
     */
    @Transactional
    public Order placeOptimistic(long productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return reserve(product, quantity);
    }

    /**
     * Atomic: no separate READ at all. One UPDATE checks and decrements, and the database
     * re-evaluates "stock >= quantity" against the latest committed row. The row is locked only for
     * that single statement, not for the fraud check.
     */
    @Transactional
    public Order placeAtomic(long productId, int quantity) {
        fraudCheck.review(productId, quantity);
        int rowsChanged = products.decrementStockIfAvailable(productId, quantity);
        if (rowsChanged == 0) {
            if (!products.existsById(productId)) {
                throw new ProductNotFoundException(productId);
            }
            throw new SoldOutException(productId);
        }
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
