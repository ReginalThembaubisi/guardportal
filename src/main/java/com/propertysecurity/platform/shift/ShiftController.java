package com.propertysecurity.platform.shift;

import com.propertysecurity.platform.shift.dto.LocationRequest;
import com.propertysecurity.platform.shift.dto.ShiftResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    /** No shift id in the path — always acts on the caller's own currently-open shift. */
    @PostMapping("/clock-out")
    public ShiftResponse clockOut(Authentication authentication, @Valid @RequestBody LocationRequest location) {
        Long guardUserId = (Long) authentication.getPrincipal();
        return ShiftResponse.from(shiftService.clockOut(guardUserId, location));
    }
}
