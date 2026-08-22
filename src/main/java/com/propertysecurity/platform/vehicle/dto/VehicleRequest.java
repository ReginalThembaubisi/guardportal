package com.propertysecurity.platform.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
        @NotBlank @Size(max = 20) String registration,
        @Size(max = 50) String make,
        @Size(max = 50) String model,
        @Size(max = 30) String colour
) {
}
