package com.propertysecurity.platform.incident.dto;

import com.propertysecurity.platform.incident.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record IncidentStatusUpdateRequest(
        @NotNull IncidentStatus status
) {
}
