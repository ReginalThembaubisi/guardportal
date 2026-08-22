package com.propertysecurity.platform.vehicle.dto;

import com.propertysecurity.platform.vehicle.Vehicle;

import java.time.LocalDateTime;

public record VehicleResponse(
        Long id,
        String registration,
        String make,
        String model,
        String colour,
        LocalDateTime createdAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getRegistration(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getColour(),
                vehicle.getCreatedAt());
    }
}
