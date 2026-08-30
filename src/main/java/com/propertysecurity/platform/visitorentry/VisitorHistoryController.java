package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.visitorentry.dto.VisitorHistoryEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Kept separate from OccupancyController/VisitorEntryController (whose
 * @PreAuthorize covers different role sets) rather than a method-level
 * override — same reasoning as OccupancyController's own comment: Spring
 * Security's class-vs-method @PreAuthorize precedence isn't worth staking
 * an access-control decision on.
 *
 * "Who visited on this day" for incident investigation — the real gap this
 * closes is that nothing else in the system lets a property manager or
 * supervisor look at visitor entries by date; the only existing history
 * views are current-occupancy (right now, not a range) and vehicle-plate
 * search (useless without a plate). Not CLIENT-facing — see
 * VisitorEntryService.historyForDateRange for why. GUARD is allowed at
 * this same gate, but VisitorEntryService.assertCanAccessPropertyForHistory
 * enforces a much narrower slice for them (today only, own property only)
 * than the open-ended range PROPERTY_MANAGER/SUPERVISOR/ADMIN get.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/visitor-entries/history")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'SUPERVISOR', 'GUARD', 'ADMIN')")
public class VisitorHistoryController {

    private final VisitorEntryService visitorEntryService;

    @GetMapping
    public List<VisitorHistoryEntryResponse> history(
            Authentication authentication,
            @PathVariable Long propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return visitorEntryService.historyForDateRange(callerUserId, propertyId, from, to).stream()
                .map(VisitorHistoryEntryResponse::from)
                .toList();
    }
}
