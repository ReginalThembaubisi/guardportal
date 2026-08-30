package com.propertysecurity.platform.shiftschedule;

import com.propertysecurity.platform.common.BaseEntity;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.shift.ShiftType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A planned/rostered shift — replaces the WhatsApp-group shift-sharing.
 * Distinct from Shift (the actual clock-in/out record): this is a plan,
 * editable and cancellable ahead of time; Shift is what actually happened.
 */
@Entity
@Table(name = "shift_schedule")
@Getter
@Setter
@NoArgsConstructor
public class ShiftSchedule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guard_id", nullable = false)
    private Guard guard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", nullable = false, length = 10)
    private ShiftType shiftType;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;
}
