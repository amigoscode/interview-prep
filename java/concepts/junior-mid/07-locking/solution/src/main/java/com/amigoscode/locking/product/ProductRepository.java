package com.amigoscode.locking.product;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // ---- pessimistic: SELECT ... FOR UPDATE, the row stays locked until the transaction ends ----

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") long id);

    // ---- atomic: check and write in a single statement, returns the number of rows changed ----

    @Modifying
    @Query("""
            update Product p
               set p.stock = p.stock - :quantity,
                   p.version = p.version + 1
             where p.id = :id
               and p.stock >= :quantity
            """)
    int decrementStockIfAvailable(@Param("id") long id, @Param("quantity") int quantity);
}
