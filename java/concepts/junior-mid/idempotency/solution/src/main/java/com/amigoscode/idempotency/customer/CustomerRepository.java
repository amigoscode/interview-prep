package com.amigoscode.idempotency.customer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("select c.balanceCents from Customer c where c.id = :id")
    Optional<Long> findBalance(@Param("id") long id);

    /**
     * Takes the money in one statement, and only if there is enough. Returns the number of rows
     * changed: 0 means the balance was too low. (Why one statement: see the locking project.)
     */
    @Modifying
    @Query("""
            update Customer c
               set c.balanceCents = c.balanceCents - :amountCents
             where c.id = :id
               and c.balanceCents >= :amountCents
            """)
    int debit(@Param("id") long id, @Param("amountCents") long amountCents);
}
