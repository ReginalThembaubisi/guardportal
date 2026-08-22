package com.propertysecurity.platform.propertymanager.dto;

import jakarta.validation.constraints.NotNull;

public record PropertyManagerLinkRequest(
        @NotNull Long userId,
        @NotNull Long propertyId
) {
}
