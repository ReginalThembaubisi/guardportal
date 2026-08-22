package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.visitorentry.dto.OccupancyResponse;
import com.propertysecurity.platform.visitorentry.dto.VisitorEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kept separate from PropertyController (which is ADMIN-only at the class
 * level) rather than relying on a method-level @PreAuthorize to override
 * it — Spring Security's class-vs-method precedence for @PreAuthorize
 * isn't worth staking an access-control decision on when a small dedicated
 * controller removes the ambiguity entirely.
 */
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GUARD', 'PROPERTY_MANAGER', 'ADMIN')")
public class OccupancyController {

    private final VisitorEntryService visitorEntryService;

    /**
     * Everyone currently on site, grouped by category. Scoped to the
     * caller's own property (guard) or managed properties (property
     * manager); unscoped for ADMIN. SUPERVISOR still has no property
     * association in the schema — see VisitorEntryService.assertCanAccessProperty.
     */
    @GetMapping("/{id}/occupancy")
    public OccupancyResponse occupancy(Authentication authentication, @PathVariable Long id) {
        Long callerUserId = (Long) authentication.getPrincipal();
        Map<VisitorCategory, List<VisitorEntry>> byCategory = visitorEntryService.occupancy(callerUserId, id);

        Map<VisitorCategory, List<VisitorEntryResponse>> responseByCategory = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<VisitorCategory, List<VisitorEntry>> categoryEntries : byCategory.entrySet()) {
            List<VisitorEntryResponse> responses = categoryEntries.getValue().stream()
                    .map(entry -> VisitorEntryResponse.from(entry, visitorEntryService.isVehicleRecognized(entry)))
                    .toList();
            responseByCategory.put(categoryEntries.getKey(), responses);
            total += responses.size();
        }

        return new OccupancyResponse(id, total, responseByCategory);
    }
}
