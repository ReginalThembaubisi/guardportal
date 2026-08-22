package com.propertysecurity.platform.unit;

import com.propertysecurity.platform.unit.dto.UnitRequest;
import com.propertysecurity.platform.unit.dto.UnitResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/api/v1/properties/{propertyId}/units")
    public List<UnitResponse> listByProperty(@PathVariable Long propertyId) {
        return unitService.listByProperty(propertyId).stream().map(UnitResponse::from).toList();
    }

    @GetMapping("/api/v1/units/{id}")
    public UnitResponse get(@PathVariable Long id) {
        return UnitResponse.from(unitService.get(id));
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
