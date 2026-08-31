package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.visitorentry.dto.ScanRequest;
import com.propertysecurity.platform.visitorentry.dto.VisitorCheckInResponse;
import com.propertysecurity.platform.visitorentry.dto.VisitorCheckOutResponse;
import com.propertysecurity.platform.visitorentry.dto.VisitorEntryResponse;
import com.propertysecurity.platform.visitorentry.dto.VisitorWalkInResponse;
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
     * The one guard-facing action: scan a QR code or type a 6-digit short
     * code to validate it and check the visitor in. Exactly one of the two
     * must be present — both or neither is a 400, not a silent fallback.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitorCheckInResponse scan(Authentication authentication, @Valid @RequestBody ScanRequest request) {
        Long guardUserId = (Long) authentication.getPrincipal();
        boolean hasQrToken = request.qrToken() != null && !request.qrToken().isBlank();
        boolean hasShortCode = request.shortCode() != null && !request.shortCode().isBlank();
        if (hasQrToken == hasShortCode) {
            throw new BadRequestException("Provide exactly one of qrToken or shortCode");
        }

        VisitorEntryService.CheckInResult result = hasShortCode
                ? visitorEntryService.checkInByShortCode(guardUserId, request.shortCode(), request.vehicleRegistration(), request.clientClaimedAt())
                : visitorEntryService.checkIn(guardUserId, request.qrToken(), request.vehicleRegistration(), request.clientClaimedAt());
        return VisitorCheckInResponse.from(result);
    }

    /** Walk-in / unexpected visitor — no invitation code, guard captures the details directly. */
    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitorWalkInResponse walkIn(Authentication authentication, @Valid @RequestBody WalkInVisitorRequest request) {
        Long guardUserId = (Long) authentication.getPrincipal();
        VisitorEntryService.WalkInResult result = visitorEntryService.checkInWalkIn(guardUserId, request);
        return VisitorWalkInResponse.from(result);
    }

    @GetMapping("/{id}")
    public VisitorEntryResponse get(@PathVariable Long id) {
        VisitorEntry entry = visitorEntryRepository.findByIdFetchVehicle(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor entry " + id + " not found"));
        return VisitorEntryResponse.from(entry, visitorEntryService.isVehicleRecognized(entry));
    }

    /** Guard-facing check-out: server-stamps exited_at on the entry. */
    @PostMapping("/{id}/exit")
    public VisitorCheckOutResponse exit(Authentication authentication, @PathVariable Long id) {
        Long guardUserId = (Long) authentication.getPrincipal();
        VisitorEntryService.CheckOutResult result = visitorEntryService.checkOut(guardUserId, id);
        return VisitorCheckOutResponse.from(result);
    }
}
