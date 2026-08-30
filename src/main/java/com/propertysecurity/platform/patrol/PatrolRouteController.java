package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.patrol.dto.PatrolRouteRequest;
import com.propertysecurity.platform.patrol.dto.PatrolRouteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patrol-routes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'ADMIN')")
public class PatrolRouteController {

    private final PatrolRouteService patrolRouteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatrolRouteResponse create(Authentication authentication, @Valid @RequestBody PatrolRouteRequest request) {
        Long callerUserId = (Long) authentication.getPrincipal();
        PatrolRouteService.Created created = patrolRouteService.create(callerUserId, request);
        return PatrolRouteResponse.from(created.route(), created.stops());
    }

    /**
     * Scoped to the caller's own managed properties (ADMIN unrestricted) —
     * needed to pick a route for missed-checkpoint status. Also
     * guard-accessible: a guard needs to discover their own property's
     * route(s) to show patrol progress, with no separate "my route"
     * endpoint.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'ADMIN', 'GUARD')")
    public List<PatrolRouteResponse> listByProperty(Authentication authentication, @RequestParam Long propertyId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return patrolRouteService.listByPropertyForCaller(callerUserId, propertyId).stream()
                .map(route -> PatrolRouteResponse.from(route, patrolRouteService.stopsFor(route.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public PatrolRouteResponse get(@PathVariable Long id) {
        PatrolRoute route = patrolRouteService.get(id);
        return PatrolRouteResponse.from(route, patrolRouteService.stopsFor(id));
    }
}
