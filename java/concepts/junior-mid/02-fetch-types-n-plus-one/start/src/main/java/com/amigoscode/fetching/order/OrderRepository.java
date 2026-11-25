package com.amigoscode.fetching.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // TODO Task 6: a query that loads the orders AND their customers in one statement, with JOIN FETCH.
    //      All orders, ordered by id.

    // TODO Task 6: a second method that does the same with @EntityGraph and no JPQL of its own.

    // TODO Task 7: a query that returns List<OrderSummary> directly ("select new ..."), no entities.

    /**
     * One page of orders with everything the page shows, fetched in one query. Somebody "fixed the
     * N+1" here by fetching the items too.
     *
     * TODO Task 9: run ./demo --endpoint page and read the RESULT line before you change this.
     */
    @Query(value = "select o from Order o join fetch o.customer join fetch o.items i join fetch i.product",
            countQuery = "select count(o) from Order o")
    Page<Order> findPage(Pageable pageable);
}
