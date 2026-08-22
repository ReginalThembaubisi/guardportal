package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.VisitorCategory;

import java.util.List;
import java.util.Map;

public record OccupancyResponse(
        Long propertyId,
        int totalOnSite,
        // All four categories always present (possibly empty) so a frontend
        // can render fixed sections without checking for key existence.
        Map<VisitorCategory, List<VisitorEntryResponse>> byCategory
) {
}
