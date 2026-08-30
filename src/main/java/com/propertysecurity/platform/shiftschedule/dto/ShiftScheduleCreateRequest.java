package com.propertysecurity.platform.shiftschedule.dto;

import com.propertysecurity.platform.shift.ShiftType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShiftScheduleCreateRequest(
        @NotNull Long guardId,
        @NotNull Long propertyId,
        @NotNull LocalDate shiftDate,
        @NotNull ShiftType shiftType,
        LocalTime startTime,
        LocalTime endTime
) {
}
