package com.propertysecurity.platform.shift;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Not a BaseEntity: no deleted_at, same reasoning as VisitorEntry — an
 * append-mostly transactional record (clocked in, later clocked out),
 * never soft-deleted.
 */
@Entity
@Table(name = "shift")
@Getter
@Setter
@NoArgsConstructor
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guard_id", nullable = false)
    private Guard guard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "clock_in_at", nullable = false)
    private LocalDateTime clockInAt;

    @Column(name = "client_claimed_clock_in_at")
    private LocalDateTime clientClaimedClockInAt;

    @Column(name = "clock_in_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal clockInLatitude;

    @Column(name = "clock_in_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal clockInLongitude;

    @Column(name = "clock_in_distance_meters")
    private Integer clockInDistanceMeters;

    @Column(name = "clock_in_within_tolerance")
    private Boolean clockInWithinTolerance;

    @Column(name = "clock_out_at")
    private LocalDateTime clockOutAt;

    @Column(name = "client_claimed_clock_out_at")
    private LocalDateTime clientClaimedClockOutAt;

    @Column(name = "clock_out_latitude", precision = 10, scale = 7)
    private BigDecimal clockOutLatitude;

    @Column(name = "clock_out_longitude", precision = 10, scale = 7)
    private BigDecimal clockOutLongitude;

    @Column(name = "clock_out_distance_meters")
    private Integer clockOutDistanceMeters;

    @Column(name = "clock_out_within_tolerance")
    private Boolean clockOutWithinTolerance;

    /**
     * Informational only — from the matching ShiftSchedule row at clock-in
     * time, or derived from the clock-in hour when there isn't one. Never
     * used in access-control or tolerance logic.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", length = 10)
    private ShiftType shiftType;

    /** Null = server-confirmed clock-out (normal path). Non-null = server could not vouch
     *  for the claimed time — see ClockOutSource for values and reasoning. */
    @Enumerated(EnumType.STRING)
    @Column(name = "clock_out_source", length = 30)
    private ClockOutSource clockOutSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
