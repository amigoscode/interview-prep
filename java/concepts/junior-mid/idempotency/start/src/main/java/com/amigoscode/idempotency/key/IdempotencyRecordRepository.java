package com.amigoscode.idempotency.key;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    /** Keys are scoped per customer: the same key from another customer is another key. */
    Optional<IdempotencyRecord> findByCustomerIdAndIdempotencyKey(long customerId, String idempotencyKey);

    // TODO Task 4: add insertIfAbsent(...): a native "insert ... on conflict ... do nothing" that
    // returns the number of rows it inserted (1 = the key is yours, 0 = somebody was first).

    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
