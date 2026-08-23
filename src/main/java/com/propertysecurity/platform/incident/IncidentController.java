package com.propertysecurity.platform.incident;

import com.propertysecurity.platform.incident.dto.IncidentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Guard-facing: report an incident. Kept separate from IncidentReadController
 * (PROPERTY_MANAGER/SUPERVISOR/ADMIN) — same reasoning as
 * VisitorEntryController vs VehicleHistoryController sharing a path
 * prefix with different roles.
 */
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GUARD')")
public class IncidentController {

    private final IncidentService incidentService;

    /**
     * Multipart, not JSON — description/severity/lat/lng as form fields,
     * photos as 0..n file parts. Requires an open shift (same as a
     * checkpoint scan).
     */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(
            Authentication authentication,
            @RequestParam String description,
            @RequestParam IncidentSeverity severity,
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
        Long guardUserId = (Long) authentication.getPrincipal();
        Incident incident = incidentService.create(guardUserId, description, severity, latitude, longitude, photos);
        return IncidentResponse.from(incident, incidentService.mediaFor(incident.getId()));
    }
}
