package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.visitorentry.dto.VisitorEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kept separate from VisitorEntryController (GUARD/ADMIN-only, since scan
 * and exit are explicitly guard actions per the build plan) so that
 * PROPERTY_MANAGER can read vehicle history without also gaining access to
 * check visitors in or out. Same reasoning as OccupancyController.
 */
@RestController
@RequestMapping("/api/v1/visitor-entries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GUARD', 'PROPERTY_MANAGER', 'ADMIN')")
public class VehicleHistoryController {

    private final VisitorEntryService visitorEntryService;

    /**
     * Every entry for a registration — "show me every time this car has
     * been on the property." Scoped per VisitorEntryService.historyForRegistration.
     */
    @GetMapping("/by-vehicle/{registration}")
    public List<VisitorEntryResponse> historyForVehicle(Authentication authentication, @PathVariable String registration) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return visitorEntryService.historyForRegistration(callerUserId, registration).stream()
                .map(entry -> VisitorEntryResponse.from(entry, visitorEntryService.isVehicleRecognized(entry)))
                .toList();
    }
}
