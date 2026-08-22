package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.invitation.Invitation;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.unit.PropertyUnit;
import com.propertysecurity.platform.vehicle.Vehicle;
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
 * Not a BaseEntity: no deleted_at in the schema — this is the paper-register
 * replacement, an append-only log of who's on site, never soft-deleted.
 */
@Entity
@Table(name = "visitor_entry")
@Getter
@Setter
@NoArgsConstructor
public class VisitorEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private PropertyUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitation_id")
    private Invitation invitation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(name = "visitor_name", nullable = false, length = 150)
    private String visitorName;

    @Column(name = "visitor_id_number", length = 30)
    private String visitorIdNumber;

    @Column(name = "visitor_phone", length = 20)
    private String visitorPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private VisitorCategory category = VisitorCategory.VISITOR;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processed_by_guard_id", nullable = false)
    private Guard processedByGuard;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "exited_at")
    private LocalDateTime exitedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exit_processed_by_guard_id")
    private Guard exitProcessedByGuard;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
