package com.propertysecurity.platform.shift.dto;

import com.propertysecurity.platform.shift.ClockOutSource;
import com.propertysecurity.platform.shift.Shift;
import com.propertysecurity.platform.shift.ShiftType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftResponse(
        Long id,
        Long guardId,
        Long propertyId,
        String propertyName,
        LocalDateTime clockInAt,
        LocalDateTime clientClaimedClockInAt,
        BigDecimal clockInLatitude,
        BigDecimal clockInLongitude,
        Integer clockInDistanceMeters,
        LocalDateTime clockOutAt,
        LocalDateTime clientClaimedClockOutAt,
        BigDecimal clockOutLatitude,
        BigDecimal clockOutLongitude,
        Integer clockOutDistanceMeters,
        ShiftType shiftType,
        /** Null = normal server-confirmed clock-out; non-null = server could not vouch for the claimed time. */
        ClockOutSource clockOutSource,
        LocalDateTime createdAt
) {
    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getGuard().getId(),
                shift.getProperty().getId(),
                shift.getProperty().getName(),
                shift.getClockInAt(),
                shift.getClientClaimedClockInAt(),
                shift.getClockInLatitude(),
                shift.getClockInLongitude(),
                shift.getClockInDistanceMeters(),
                shift.getClockOutAt(),
                shift.getClientClaimedClockOutAt(),
                shift.getClockOutLatitude(),
                shift.getClockOutLongitude(),
                shift.getClockOutDistanceMeters(),
                shift.getShiftType(),
                shift.getClockOutSource(),
                shift.getCreatedAt());
    }
}
