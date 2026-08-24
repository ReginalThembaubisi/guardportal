package com.propertysecurity.platform.propertyclient.dto;

import com.propertysecurity.platform.propertyclient.PropertyClient;

public record PropertyClientResponse(
        Long id,
        Long userId,
        Long propertyId,
        String propertyName
) {
    public static PropertyClientResponse from(PropertyClient link) {
        return new PropertyClientResponse(
                link.getId(),
                link.getUser().getId(),
                link.getProperty().getId(),
                link.getProperty().getName());
    }
}
