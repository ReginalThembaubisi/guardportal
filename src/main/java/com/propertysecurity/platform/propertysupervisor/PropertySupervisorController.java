package com.propertysecurity.platform.propertysupervisor;

import com.propertysecurity.platform.propertysupervisor.dto.PropertySupervisorLinkRequest;
import com.propertysecurity.platform.propertysupervisor.dto.PropertySupervisorResponse;
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
@RequestMapping("/api/v1/property-supervisors")
@RequiredArgsConstructor
public class PropertySupervisorController {

    private final PropertySupervisorService propertySupervisorService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertySupervisorResponse link(@Valid @RequestBody PropertySupervisorLinkRequest request) {
        return PropertySupervisorResponse.from(propertySupervisorService.link(request));
    }

    /** The properties the caller themselves supervises — powers the incidents screen's property selector. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public List<PropertySupervisorResponse> mine(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return propertySupervisorService.supervisedProperties(userId).stream()
                .map(PropertySupervisorResponse::from)
                .toList();
    }
}
