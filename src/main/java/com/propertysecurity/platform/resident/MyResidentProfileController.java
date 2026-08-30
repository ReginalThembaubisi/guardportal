package com.propertysecurity.platform.resident;

import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.resident.dto.ResidentProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A resident's own unit/property — for the Home page header. Kept separate
 * from ResidentController (PROPERTY_MANAGER/ADMIN-only, for managing other
 * residents), the same split already used for MyVisitorEntriesController vs
 * VisitorEntryController.
 */
@RestController
@RequestMapping("/api/v1/residents/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class MyResidentProfileController {

    private final ResidentRepository residentRepository;

    @GetMapping
    public ResidentProfileResponse me(Authentication authentication) {
        Long residentUserId = (Long) authentication.getPrincipal();
        Resident resident = residentRepository.findByUser_IdAndDeletedAtIsNullFetchUnitAndProperty(residentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No resident profile found for this account"));
        return ResidentProfileResponse.from(resident);
    }
}
