package com.propertysecurity.platform.propertyclient.dto;

import jakarta.validation.constraints.NotNull;

public record PropertyClientLinkRequest(
        @NotNull Long userId,
        @NotNull Long propertyId
) {
}
