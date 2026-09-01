package com.propertysecurity.platform.shift;

import com.propertysecurity.platform.shift.dto.LocationRequest;
import com.propertysecurity.platform.shift.dto.ShiftCoverageSlot;
import com.propertysecurity.platform.shift.dto.ShiftResponse;
import com.propertysecurity.platform.shift.dto.ShiftSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GUARD')")
public class ShiftController {

    private final ShiftService shiftService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftResponse clockIn(Authentication authentication, @Valid @RequestBody LocationRequest location) {
        Long guardUserId = (Long) authentication.getPrincipal();
        return ShiftResponse.from(shiftService.clockIn(guardUserId, location));
    }

    /**
     * Lets the guard app recover clocked-in/out state on load (after a fresh
     * login, reinstall, or just reopening the app) instead of trusting
     * whatever it had cached locally. 204 when nothing is open.
     */
    @GetMapping("/current")
    public ResponseEntity<ShiftResponse> getCurrentShift(Authentication authentication) {
        Long guardUserId = (Long) authentication.getPrincipal();
        return shiftService.getCurrentShift(guardUserId)
                .map(shift -> ResponseEntity.ok(ShiftResponse.from(shift)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** No shift id in the path — always acts on the caller's own currently-open shift. */
    @PostMapping("/clock-out")
    public ShiftResponse clockOut(Authentication authentication, @Valid @RequestBody LocationRequest location) {
        Long guardUserId = (Long) authentication.getPrincipal();
        return ShiftResponse.from(shiftService.clockOut(guardUserId, location));
    }

    /**
     * Supervisor/admin shift list for a property — at most 50 rows, newest first.
     * Four states are visible via clockOutSource + clockOutAt (see ShiftSummaryResponse).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'PROPERTY_MANAGER', 'ADMIN')")
    public List<ShiftSummaryResponse> listForProperty(Authentication authentication, @RequestParam Long propertyId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return shiftService.listForProperty(callerUserId, propertyId);
    }

    /**
     * Rostered-vs-worked coverage for a property and date range.
     * Each slot represents one scheduled shift: WORKED, OPEN (still clocked in), or NO_SHOW.
     * No-show slots have null shift fields.
     */
    @GetMapping("/coverage")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'PROPERTY_MANAGER', 'ADMIN')")
    public List<ShiftCoverageSlot> coverage(Authentication authentication,
                                            @RequestParam Long propertyId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return shiftService.coverageForProperty(callerUserId, propertyId, from, to);
    }
}
