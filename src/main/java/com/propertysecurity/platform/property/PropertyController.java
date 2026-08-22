package com.propertysecurity.platform.property;

import com.propertysecurity.platform.property.dto.PropertyRequest;
import com.propertysecurity.platform.property.dto.PropertyResponse;
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
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@Valid @RequestBody PropertyRequest request) {
        return PropertyResponse.from(propertyService.create(request));
    }

    @GetMapping
    public List<PropertyResponse> listAll() {
        return propertyService.listAll().stream().map(PropertyResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PropertyResponse get(@PathVariable Long id) {
        return PropertyResponse.from(propertyService.get(id));
    }

    @PutMapping("/{id}")
    public PropertyResponse update(@PathVariable Long id, @Valid @RequestBody PropertyRequest request) {
        return PropertyResponse.from(propertyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        propertyService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
