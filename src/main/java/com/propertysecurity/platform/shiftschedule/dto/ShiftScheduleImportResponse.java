package com.propertysecurity.platform.shiftschedule.dto;

import java.util.List;

public record ShiftScheduleImportResponse(
        int createdCount,
        int skippedCount,
        List<ShiftScheduleImportResultRow> rows
) {
}
