package com.amigoscode.pagination.order;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ---- Offset pagination: LIMIT ? OFFSET ?, written by Spring Data from the Pageable ----

    /** A Slice knows its rows and whether there are more. It never counts the table. */
    Slice<Order> findAllBy(Pageable pageable);

    Slice<Order> findByCustomerId(long customerId, Pageable pageable);

    // ---- Keyset pagination: no OFFSET. The WHERE clause starts where the last page ended ----

    @Query(value = """
            select * from orders
             order by created_at desc, id desc
             limit :limit
            """, nativeQuery = true)
    List<Order> feedFirstPage(@Param("limit") int limit);

    /**
     * (created_at, id) < (?, ?) is a row-value comparison: it compares created_at first and uses id
     * only to break a tie, exactly like the ORDER BY. Postgres answers it from the index on
     * (created_at, id) by jumping straight to that position.
     */
    @Query(value = """
            select * from orders
             where (created_at, id) < (:createdAt, :id)
             order by created_at desc, id desc
             limit :limit
            """, nativeQuery = true)
    List<Order> feedAfter(@Param("createdAt") Instant createdAt, @Param("id") long id, @Param("limit") int limit);
}
