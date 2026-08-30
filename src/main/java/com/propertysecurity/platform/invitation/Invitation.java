package com.propertysecurity.platform.invitation;

import com.propertysecurity.platform.resident.Resident;
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
 * Not a BaseEntity: no deleted_at in the schema. Lifecycle is tracked via
 * status (PENDING/USED/EXPIRED/CANCELLED) instead of soft delete.
 */
@Entity
@Table(name = "invitation")
@Getter
@Setter
@NoArgsConstructor
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    @Column(name = "visitor_name", nullable = false, length = 150)
    private String visitorName;

    @Column(name = "visitor_phone", length = 20)
    private String visitorPhone;

    @Column(name = "expected_vehicle_reg", length = 20)
    private String expectedVehicleReg;

    @Column(name = "purpose", length = 255)
    private String purpose;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;

    @Column(name = "qr_token", nullable = false, unique = true, length = 36)
    private String qrToken;

    /**
     * Not globally unique (see V13__add_invitation_short_code.sql) — uniqueness
     * is scoped to overlapping-and-PENDING invitations at the same property,
     * enforced in InvitationService at generation time.
     */
    @Column(name = "short_code", nullable = false, length = 6)
    private String shortCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
