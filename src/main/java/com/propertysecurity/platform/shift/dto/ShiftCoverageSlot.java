package com.propertysecurity.platform.shift.dto;

import com.propertysecurity.platform.shift.ClockOutSource;
import com.propertysecurity.platform.shift.CoverageStatus;
import com.propertysecurity.platform.shift.Shift;
import com.propertysecurity.platform.shiftschedule.ShiftSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShiftCoverageSlot(
        Long scheduleId,
        Long guardId,
        String guardName,
        LocalDate shiftDate,
        String shiftType,
        LocalTime startTime,
        LocalTime endTime,
        CoverageStatus status,
        // null when status == NO_SHOW
        Long shiftId,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        ClockOutSource clockOutSource
) {
    public static ShiftCoverageSlot worked(ShiftSchedule schedule, Shift shift) {
        return from(schedule, shift, CoverageStatus.WORKED);
    }

    public static ShiftCoverageSlot open(ShiftSchedule schedule, Shift shift) {
        return from(schedule, shift, CoverageStatus.OPEN);
    }

    public static ShiftCoverageSlot noShow(ShiftSchedule schedule) {
        return new ShiftCoverageSlot(
                schedule.getId(),
                schedule.getGuard().getId(),
                schedule.getGuard().getUser().getFullName(),
                schedule.getShiftDate(),
                schedule.getShiftType().name(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                CoverageStatus.NO_SHOW,
                null, null, null, null);
    }

    private static ShiftCoverageSlot from(ShiftSchedule schedule, Shift shift, CoverageStatus status) {
        return new ShiftCoverageSlot(
                schedule.getId(),
                schedule.getGuard().getId(),
                schedule.getGuard().getUser().getFullName(),
                schedule.getShiftDate(),
                schedule.getShiftType().name(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                status,
                shift.getId(),
                shift.getClockInAt(),
                shift.getClockOutAt(),
                shift.getClockOutSource());
    }
}
