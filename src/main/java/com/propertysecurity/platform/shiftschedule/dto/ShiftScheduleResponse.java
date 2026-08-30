package com.propertysecurity.platform.shiftschedule.dto;

import com.propertysecurity.platform.shift.ShiftType;
import com.propertysecurity.platform.shiftschedule.ShiftSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShiftScheduleResponse(
        Long id,
        Long guardId,
        String guardName,
        Long propertyId,
        String propertyName,
        LocalDate shiftDate,
        ShiftType shiftType,
        LocalTime startTime,
        LocalTime endTime,
        LocalDateTime createdAt
) {
    public static ShiftScheduleResponse from(ShiftSchedule schedule) {
        return new ShiftScheduleResponse(
                schedule.getId(),
                schedule.getGuard().getId(),
                schedule.getGuard().getUser().getFullName(),
                schedule.getProperty().getId(),
                schedule.getProperty().getName(),
                schedule.getShiftDate(),
                schedule.getShiftType(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getCreatedAt());
    }
}
