package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.visitorentry.dto.ScanRequest;
import com.propertysecurity.platform.visitorentry.dto.VisitorEntryResponse;
import com.propertysecurity.platform.visitorentry.dto.WalkInVisitorRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/visitor-entries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GUARD', 'ADMIN')")
public class VisitorEntryController {

    private final VisitorEntryService visitorEntryService;
    private final VisitorEntryRepository visitorEntryRepository;

    /**
     * The one guard-facing action: scan (or manually key in) a QR token to
     * validate it and check the visitor in. Same endpoint either way — a
     * scanned QR and a manually typed token both just arrive as a string.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitorEntryResponse scan(Authentication authentication, @Valid @RequestBody ScanRequest request) {
        Long guardUserId = (Long) authentication.getPrincipal();
        VisitorEntryService.CheckInResult result = visitorEntryService.checkIn(guardUserId, request.qrToken(), request.vehicleRegistration());
        return VisitorEntryResponse.from(result.entry(), result.vehicleRecognized());
    }

    /** Walk-in / unexpected visitor — no invitation code, guard captures the details directly. */
    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitorEntryResponse walkIn(Authentication authentication, @Valid @RequestBody WalkInVisitorRequest request) {
        Long guardUserId = (Long) authentication.getPrincipal();
        VisitorEntry entry = visitorEntryService.checkInWalkIn(guardUserId, request);
        return VisitorEntryResponse.from(entry, visitorEntryService.isVehicleRecognized(entry));
    }

    @GetMapping("/{id}")
    public VisitorEntryResponse get(@PathVariable Long id) {
        VisitorEntry entry = visitorEntryRepository.findByIdFetchVehicle(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor entry " + id + " not found"));
        return VisitorEntryResponse.from(entry, visitorEntryService.isVehicleRecognized(entry));
    }

    /** Guard-facing check-out: server-stamps exited_at on the entry. */
    @PostMapping("/{id}/exit")
    public VisitorEntryResponse exit(Authentication authentication, @PathVariable Long id) {
        Long guardUserId = (Long) authentication.getPrincipal();
        VisitorEntry entry = visitorEntryService.checkOut(guardUserId, id);
        return VisitorEntryResponse.from(entry, visitorEntryService.isVehicleRecognized(entry));
    }
}
