package com.propertysecurity.platform.propertyclient;

import com.propertysecurity.platform.propertyclient.dto.PropertyClientLinkRequest;
import com.propertysecurity.platform.propertyclient.dto.PropertyClientResponse;
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
@RequestMapping("/api/v1/property-clients")
@RequiredArgsConstructor
public class PropertyClientController {

    private final PropertyClientService propertyClientService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyClientResponse link(@Valid @RequestBody PropertyClientLinkRequest request) {
        return PropertyClientResponse.from(propertyClientService.link(request));
    }

    /** The properties the caller themselves owns — powers the roster screen's property selector. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public List<PropertyClientResponse> mine(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return propertyClientService.ownedProperties(userId).stream()
                .map(PropertyClientResponse::from)
                .toList();
    }
}
