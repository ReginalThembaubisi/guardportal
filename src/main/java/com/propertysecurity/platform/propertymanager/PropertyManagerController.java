package com.propertysecurity.platform.propertymanager;

import com.propertysecurity.platform.propertymanager.dto.PropertyManagerLinkRequest;
import com.propertysecurity.platform.propertymanager.dto.PropertyManagerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/property-managers")
@RequiredArgsConstructor
public class PropertyManagerController {

    private final PropertyManagerService propertyManagerService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyManagerResponse link(@Valid @RequestBody PropertyManagerLinkRequest request) {
        return PropertyManagerResponse.from(propertyManagerService.link(request));
    }

    /** The properties the caller themselves manages — powers the dashboard's property selector. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('PROPERTY_MANAGER')")
    public List<PropertyManagerResponse> mine(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return propertyManagerService.managedProperties(userId).stream()
                .map(PropertyManagerResponse::from)
                .toList();
    }
}
