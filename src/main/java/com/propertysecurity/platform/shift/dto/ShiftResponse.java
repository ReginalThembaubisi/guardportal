package com.propertysecurity.platform.shift.dto;

import com.propertysecurity.platform.shift.Shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShiftResponse(
        Long id,
        Long guardId,
        Long propertyId,
        String propertyName,
        LocalDateTime clockInAt,
        BigDecimal clockInLatitude,
        BigDecimal clockInLongitude,
        Integer clockInDistanceMeters,
        Boolean clockInWithinTolerance,
        LocalDateTime clockOutAt,
        BigDecimal clockOutLatitude,
        BigDecimal clockOutLongitude,
        Integer clockOutDistanceMeters,
        Boolean clockOutWithinTolerance,
        LocalDateTime createdAt
) {
    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getGuard().getId(),
                shift.getProperty().getId(),
                shift.getProperty().getName(),
                shift.getClockInAt(),
                shift.getClockInLatitude(),
                shift.getClockInLongitude(),
                shift.getClockInDistanceMeters(),
                shift.getClockInWithinTolerance(),
                shift.getClockOutAt(),
                shift.getClockOutLatitude(),
                shift.getClockOutLongitude(),
                shift.getClockOutDistanceMeters(),
                shift.getClockOutWithinTolerance(),
                shift.getCreatedAt());
    }
}
