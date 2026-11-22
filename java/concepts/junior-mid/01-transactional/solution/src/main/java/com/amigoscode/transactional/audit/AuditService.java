package com.amigoscode.transactional.audit;

import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit log. Security reads it to see every attempt, including the ones that failed. */
@Service
public class AuditService {

    private final AuditRepository auditLog;

    public AuditService(AuditRepository auditLog) {
        this.auditLog = auditLog;
    }

    /**
     * REQUIRES_NEW: the caller's transaction is paused and this row is written and committed in a
     * transaction of its own. Whatever the caller does afterwards, the row stays.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String detail) {
        TransactionTrace.record("AuditService.record");
        auditLog.save(new AuditEntry(action, detail));
    }
}
