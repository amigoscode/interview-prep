package com.amigoscode.pagination.order;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ---- Offset pagination: LIMIT ? OFFSET ?, written by Spring Data from the Pageable ----

    // findAll(Pageable) is inherited from JpaRepository. It returns a Page.

    Page<Order> findByCustomerId(long customerId, Pageable pageable);

    // ---- Keyset pagination: no OFFSET. The WHERE clause starts where the last page ended ----

    @Query(value = """
            select * from orders
             order by created_at desc
             limit :limit
            """, nativeQuery = true)
    List<Order> feedFirstPage(@Param("limit") int limit);

    @Query(value = """
            select * from orders
             where created_at < :createdAt
             order by created_at desc
             limit :limit
            """, nativeQuery = true)
    List<Order> feedAfter(@Param("createdAt") Instant createdAt, @Param("limit") int limit);
}
