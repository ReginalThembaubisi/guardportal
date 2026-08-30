package com.propertysecurity.platform.shiftschedule.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShiftScheduleImportRequest(
        @NotNull Long propertyId,
        @NotEmpty List<ShiftScheduleImportRow> rows
) {
}
