package com.propertysecurity.platform.patrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PatrolRouteRequest(
        @NotNull Long propertyId,
        @NotBlank @Size(max = 150) String name,
        /** Order of this list becomes the route's checkpoint sequence (1-indexed). */
        @NotEmpty List<Long> checkpointIds
) {
}
