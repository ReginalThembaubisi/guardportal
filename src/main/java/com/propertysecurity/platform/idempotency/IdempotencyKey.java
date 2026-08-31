package com.propertysecurity.platform.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_key")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key", nullable = false, length = 36, unique = true)
    private String idemKey;

    /** Method + path that first claimed this key, e.g. "POST /api/v1/shifts". */
    @Column(name = "endpoint", nullable = false, length = 150)
    private String endpoint;

    @Column(name = "principal_id", nullable = false)
    private Long principalId;

    /** True while the request is being processed; false once finalised or reclaimed. */
    @Column(name = "in_flight", nullable = false)
    private boolean inFlight = true;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_body", columnDefinition = "MEDIUMTEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
