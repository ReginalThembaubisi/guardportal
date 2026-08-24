package com.propertysecurity.platform.resident.dto;

/** One row's outcome from a bulk import — reported back so a bad row doesn't hide among 1000 good ones. */
public record ResidentImportResultRow(
        int rowNumber,
        String unitNumber,
        String fullName,
        boolean created,
        String reason
) {
}
