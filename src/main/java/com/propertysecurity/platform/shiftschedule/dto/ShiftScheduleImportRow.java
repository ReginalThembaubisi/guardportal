package com.propertysecurity.platform.shiftschedule.dto;

/**
 * One row from a bulk shift-schedule import (see
 * ShiftScheduleService.importSchedules). Deliberately all-String and
 * unvalidated by annotations, same reasoning as ResidentImportRow: a bad
 * date/shiftType/guard phone number should skip *this row* with a reason,
 * not reject the whole batch with a 400. startTime/endTime are optional.
 */
public record ShiftScheduleImportRow(
        String guardPhoneNumber,
        String shiftDate,
        String shiftType,
        String startTime,
        String endTime
) {
}
