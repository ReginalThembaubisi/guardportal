package com.propertysecurity.platform.propertysupervisor.dto;

import jakarta.validation.constraints.NotNull;

public record PropertySupervisorLinkRequest(
        @NotNull Long userId,
        @NotNull Long propertyId
) {
}
