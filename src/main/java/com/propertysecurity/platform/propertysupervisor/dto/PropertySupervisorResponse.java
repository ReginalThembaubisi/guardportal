package com.propertysecurity.platform.propertysupervisor.dto;

import com.propertysecurity.platform.propertysupervisor.PropertySupervisor;

public record PropertySupervisorResponse(
        Long id,
        Long userId,
        Long propertyId,
        String propertyName
) {
    public static PropertySupervisorResponse from(PropertySupervisor link) {
        return new PropertySupervisorResponse(
                link.getId(),
                link.getUser().getId(),
                link.getProperty().getId(),
                link.getProperty().getName());
    }
}
