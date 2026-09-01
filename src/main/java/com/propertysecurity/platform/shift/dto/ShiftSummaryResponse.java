package com.propertysecurity.platform.shift.dto;

import com.propertysecurity.platform.shift.ClockOutSource;
import com.propertysecurity.platform.shift.Shift;
import com.propertysecurity.platform.shift.ShiftType;

import java.time.LocalDateTime;

/**
 * Supervisor/admin view of a single shift row — all four states:
 *   clockOutSource=null, clockOutAt set   → Verified
 *   clockOutSource=CLIENT_CLAIMED_LATE    → End unverified
 *   clockOutSource=ROSTER_AUTO_CLOSED     → Auto-closed
 *   clockOutAt=null                       → Open
 *
 * weekAutoCloseOrdinal: 1-based rank of this shift among the guard's
 * ROSTER_AUTO_CLOSED shifts in the same ISO week. 0 for non-auto-closed rows.
 * The frontend shows "Nth auto-close this week" when ordinal > 1.
 */
public record ShiftSummaryResponse(
        Long id,
        Long guardId,
        String guardName,
        Long propertyId,
        String propertyName,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        ShiftType shiftType,
        ClockOutSource clockOutSource,
        int weekAutoCloseOrdinal,
        Boolean clockInWithinTolerance,
        Integer clockInDistanceMeters,
        Boolean clockOutWithinTolerance,
        Integer clockOutDistanceMeters,
        LocalDateTime createdAt
) {
    public static ShiftSummaryResponse from(Shift shift, int weekAutoCloseOrdinal) {
        return new ShiftSummaryResponse(
                shift.getId(),
                shift.getGuard().getId(),
                shift.getGuard().getUser().getFullName(),
                shift.getProperty().getId(),
                shift.getProperty().getName(),
                shift.getClockInAt(),
                shift.getClockOutAt(),
                shift.getShiftType(),
                shift.getClockOutSource(),
                weekAutoCloseOrdinal,
                shift.getClockInWithinTolerance(),
                shift.getClockInDistanceMeters(),
                shift.getClockOutWithinTolerance(),
                shift.getClockOutDistanceMeters(),
                shift.getCreatedAt());
    }
}
