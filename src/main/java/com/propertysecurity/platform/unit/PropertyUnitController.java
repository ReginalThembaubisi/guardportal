package com.propertysecurity.platform.unit;

import com.propertysecurity.platform.unit.dto.UnitRequest;
import com.propertysecurity.platform.unit.dto.UnitResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Write operations only — per docs/build_plan.md Phase 1, unit management
 * is ADMIN-only. Reads are split into UnitReadController (see its own
 * docstring for why a separate class rather than a method-level
 * @PreAuthorize override) so a property manager can browse units on their
 * own property without being able to create/edit/delete them.
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PropertyUnitController {

    private final PropertyUnitService unitService;

    @PostMapping("/api/v1/properties/{propertyId}/units")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse create(@PathVariable Long propertyId, @Valid @RequestBody UnitRequest request) {
        return UnitResponse.from(unitService.create(propertyId, request));
    }

    @PutMapping("/api/v1/units/{id}")
    public UnitResponse update(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return UnitResponse.from(unitService.update(id, request));
    }

    @DeleteMapping("/api/v1/units/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        unitService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
