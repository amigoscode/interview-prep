package com.amigoscode.transactional.audit;

import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit log. Security reads it to see every attempt, including the ones that failed. */
@Service
public class AuditService {

    private final AuditRepository auditLog;

    public AuditService(AuditRepository auditLog) {
        this.auditLog = auditLog;
    }

    // TODO Task 6: when the caller's transaction rolls back, this row disappears with it. It must stay.
    @Transactional
    public void record(String action, String detail) {
        TransactionTrace.record("AuditService.record");
        auditLog.save(new AuditEntry(action, detail));
    }
}
