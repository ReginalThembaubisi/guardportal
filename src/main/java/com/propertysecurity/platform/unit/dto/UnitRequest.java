package com.propertysecurity.platform.unit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnitRequest(
        @NotBlank @Size(max = 30) String unitNumber
) {
}
