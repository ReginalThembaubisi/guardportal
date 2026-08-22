package com.propertysecurity.platform.checkpoint.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CheckpointRequest(
        @NotNull Long propertyId,
        @NotBlank @Size(max = 150) String name,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Positive Integer geoToleranceMeters
) {
}
