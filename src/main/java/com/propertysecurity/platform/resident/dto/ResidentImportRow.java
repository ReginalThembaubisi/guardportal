package com.propertysecurity.platform.resident.dto;

/**
 * One row from a bulk resident import (see ResidentService.importResidents).
 * Deliberately unvalidated by annotations: ResidentImportRequest.rows isn't
 * marked @Valid, so bean validation never cascades into these fields — that's
 * intentional, not an oversight. A blank field here should skip *this row*
 * with a reason (ResidentService.validateImportRow), not reject the whole
 * batch with a 400. Email is optional, same as ResidentRequest.
 */
public record ResidentImportRow(
        String unitNumber,
        String fullName,
        String phoneNumber,
        String email
) {
}
