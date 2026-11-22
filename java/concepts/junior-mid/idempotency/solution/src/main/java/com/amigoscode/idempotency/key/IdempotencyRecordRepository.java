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

    /**
     * Claims the key. Returns 1 if this request inserted the row, 0 if a row for this customer and
     * key already exists. If another transaction is inserting the same key right now, this
     * statement waits until that transaction commits or rolls back.
     */
    @Modifying
    @Query(nativeQuery = true, value = "insert into idempotency_key (customer_id, idempotency_key, request_hash) "
            + "values (:customerId, :idempotencyKey, :requestHash) "
            + "on conflict (customer_id, idempotency_key) do nothing")
    int insertIfAbsent(@Param("customerId") long customerId,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("requestHash") String requestHash);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
