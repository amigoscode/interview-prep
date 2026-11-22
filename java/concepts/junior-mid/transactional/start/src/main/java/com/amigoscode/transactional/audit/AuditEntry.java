package com.amigoscode.transactional.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;

    private String detail;

    private Instant createdAt = Instant.now();

    protected AuditEntry() {
    }

    public AuditEntry(String action, String detail) {
        this.action = action;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
