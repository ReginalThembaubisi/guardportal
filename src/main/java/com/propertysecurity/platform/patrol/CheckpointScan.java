package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.checkpoint.Checkpoint;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.shift.Shift;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Not a BaseEntity: no deleted_at — an append-only proof-of-presence log,
 * same reasoning as VisitorEntry/Shift.
 */
@Entity
@Table(name = "checkpoint_scan")
@Getter
@Setter
@NoArgsConstructor
public class CheckpointScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    private Checkpoint checkpoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guard_id", nullable = false)
    private Guard guard;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "within_tolerance")
    private Boolean withinTolerance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
