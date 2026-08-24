package com.propertysecurity.platform.resident.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ResidentImportRequest(
        @NotNull Long propertyId,
        @NotEmpty List<ResidentImportRow> rows
) {
}
