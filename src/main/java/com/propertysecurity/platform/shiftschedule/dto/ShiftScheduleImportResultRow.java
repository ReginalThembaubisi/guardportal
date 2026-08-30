package com.propertysecurity.platform.shiftschedule.dto;

/** One row's outcome from a bulk import — reported back so a bad row doesn't hide among many good ones. */
public record ShiftScheduleImportResultRow(
        int rowNumber,
        String guardPhoneNumber,
        String shiftDate,
        boolean created,
        String reason
) {
}
