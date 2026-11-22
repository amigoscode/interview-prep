package com.amigoscode.idempotency.key;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keys do not live for ever: every row has an expires_at (24 hours after it was created). A real
 * application calls this from a scheduled job, for example once an hour with @Scheduled. Here the
 * demo and the tests call it directly.
 */
@Service
public class IdempotencyCleanup {

    private final IdempotencyRecordRepository keys;

    public IdempotencyCleanup(IdempotencyRecordRepository keys) {
        this.keys = keys;
    }

    /** Deletes every key whose expires_at is in the past. Returns how many rows were deleted. */
    @Transactional
    public int deleteExpired() {
        return keys.deleteExpired(Instant.now());
    }
}
