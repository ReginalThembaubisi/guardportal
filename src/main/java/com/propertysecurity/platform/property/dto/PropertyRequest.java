package com.propertysecurity.platform.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PropertyRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 255) String address,
        @Size(max = 50) String timezone
) {
}
