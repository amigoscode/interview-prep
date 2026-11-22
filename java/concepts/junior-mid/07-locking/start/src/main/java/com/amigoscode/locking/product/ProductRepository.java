package com.amigoscode.locking.product;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // ---- used by the naive strategy: one read, one write, nothing tying them together ----

    @Query("select p.stock from Product p where p.id = :id")
    Optional<Integer> findStock(@Param("id") long id);

    @Modifying
    @Query("update Product p set p.stock = :stock where p.id = :id")
    void writeStock(@Param("id") long id, @Param("stock") int stock);

    // TODO Task 2: add a query that reads a product and locks its row until the transaction ends.

    // TODO Task 6: add a query that checks the stock and decrements it in a single UPDATE,
    //      and returns how many rows it changed.
}
