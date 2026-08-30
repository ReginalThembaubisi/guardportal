package com.propertysecurity.platform.unit;

import com.propertysecurity.platform.unit.dto.UnitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kept separate from PropertyUnitController (ADMIN-only at the class
 * level, per docs/build_plan.md Phase 1) rather than a method-level
 * @PreAuthorize override — same reasoning as OccupancyController: Spring
 * Security's class-vs-method precedence for @PreAuthorize isn't worth
 * staking an access-control decision on. A property manager can browse
 * (but not create/edit/delete) units on their own managed properties —
 * needed to pick a destination unit when creating a resident. A guard can
 * browse units on their own assigned property — needed to link a walk-in
 * visitor to a destination unit (see VisitorEntryController.walkIn).
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GUARD', 'PROPERTY_MANAGER', 'ADMIN')")
public class UnitReadController {

    private final PropertyUnitService unitService;

    @GetMapping("/api/v1/properties/{propertyId}/units")
    public List<UnitResponse> listByProperty(Authentication authentication, @PathVariable Long propertyId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return unitService.listByPropertyForCaller(callerUserId, propertyId).stream().map(UnitResponse::from).toList();
    }

    @GetMapping("/api/v1/units/{id}")
    public UnitResponse get(Authentication authentication, @PathVariable Long id) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return UnitResponse.from(unitService.getForCaller(callerUserId, id));
    }
}
