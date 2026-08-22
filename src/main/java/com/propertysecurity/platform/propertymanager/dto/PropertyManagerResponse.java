package com.propertysecurity.platform.propertymanager.dto;

import com.propertysecurity.platform.propertymanager.PropertyManager;

public record PropertyManagerResponse(
        Long id,
        Long userId,
        Long propertyId,
        String propertyName
) {
    public static PropertyManagerResponse from(PropertyManager link) {
        return new PropertyManagerResponse(
                link.getId(),
                link.getUser().getId(),
                link.getProperty().getId(),
                link.getProperty().getName());
    }
}
