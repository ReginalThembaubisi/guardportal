package com.propertysecurity.platform.resident.dto;

import java.util.List;

public record ResidentImportResponse(
        int createdCount,
        int skippedCount,
        List<ResidentImportResultRow> rows
) {
}
