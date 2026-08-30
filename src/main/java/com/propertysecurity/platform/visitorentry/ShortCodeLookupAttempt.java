package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.property.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A plain, non-hash-chained security/ops log of failed short-code check-in
 * attempts — brute-force visibility, not a legal audit trail. Deliberately
 * separate from audit_log: that table's schema and append-only guarantee
 * are scoped to real entity writes (entity_id NOT NULL, a closed
 * CREATE/UPDATE/SOFT_DELETE action set), and a failed lookup creates
 * nothing to attach a row to. See V13__add_invitation_short_code.sql.
 */
@Entity
@Table(name = "short_code_lookup_attempt")
@Getter
@Setter
@NoArgsConstructor
public class ShortCodeLookupAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guard_id", nullable = false)
    private Guard guard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private CheckInRejectionReason reason;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    @PrePersist
    protected void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = LocalDateTime.now();
        }
    }
}
