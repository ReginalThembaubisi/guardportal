package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.visitorentry.dto.VisitorEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Resident-facing visitor history — the Phase 4 "resident portal" piece
 * (backend only; the frontend view lives in frontend/resident-dashboard).
 * Kept separate from VisitorEntryController (GUARD/ADMIN-only).
 */
@RestController
@RequestMapping("/api/v1/visitor-entries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class MyVisitorEntriesController {

    private final VisitorEntryService visitorEntryService;

    @GetMapping("/mine")
    public List<VisitorEntryResponse> mine(Authentication authentication) {
        Long residentUserId = (Long) authentication.getPrincipal();
        return visitorEntryService.myHistory(residentUserId).stream()
                .map(entry -> VisitorEntryResponse.from(entry, visitorEntryService.isVehicleRecognized(entry)))
                .toList();
    }
}
