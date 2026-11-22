package com.amigoscode.fetching.order;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** JOIN FETCH: "join the customer AND fill order.customer from the same rows". One statement. */
    @Query("select o from Order o join fetch o.customer order by o.id")
    List<Order> findAllJoinFetchCustomer();

    /**
     * The same idea, declared instead of written: the query comes from the method name, and the
     * entity graph says which associations to load with it.
     */
    @EntityGraph(attributePaths = "customer")
    List<Order> findAllByOrderById();

    /** No entities at all: the database returns exactly the five columns the list shows. */
    @Query("""
            select new com.amigoscode.fetching.order.OrderSummary(o.id, o.status, o.placedAt, c.name, c.city)
              from Order o
              join o.customer c
             order by o.id
            """)
    List<OrderSummary> findAllSummaries();

    /**
     * One page of orders. It fetches the customer (to-one: one row per order, so the database can
     * still cut the page with a limit). It does NOT fetch the items: a join on a collection gives
     * several rows per order, and then the database cannot count orders any more. The items are
     * loaded afterwards, in one batch (see @BatchSize on Order.items).
     */
    @Query(value = "select o from Order o join fetch o.customer",
            countQuery = "select count(o) from Order o")
    Page<Order> findPage(Pageable pageable);
}
