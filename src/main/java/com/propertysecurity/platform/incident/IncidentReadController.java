package com.propertysecurity.platform.incident;

import com.propertysecurity.platform.incident.dto.IncidentResponse;
import com.propertysecurity.platform.incident.dto.IncidentStatusUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

/**
 * Viewer-facing: browse and triage incidents for a property, and update
 * status. Kept separate from IncidentController (GUARD-only) — see that
 * class's docstring.
 */
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'SUPERVISOR', 'ADMIN')")
public class IncidentReadController {

    private final IncidentService incidentService;
    private final EvidencePackService evidencePackService;

    @GetMapping
    public List<IncidentResponse> listByProperty(Authentication authentication, @RequestParam Long propertyId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return incidentService.listForProperty(callerUserId, propertyId).stream()
                .map(incident -> IncidentResponse.from(incident, incidentService.mediaFor(incident.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public IncidentResponse get(Authentication authentication, @PathVariable Long id) {
        Long callerUserId = (Long) authentication.getPrincipal();
        Incident incident = incidentService.get(callerUserId, id);
        return IncidentResponse.from(incident, incidentService.mediaFor(incident.getId()));
    }

    @PatchMapping("/{id}/status")
    public IncidentResponse updateStatus(
            Authentication authentication, @PathVariable Long id, @Valid @RequestBody IncidentStatusUpdateRequest request) {
        Long callerUserId = (Long) authentication.getPrincipal();
        Incident incident = incidentService.updateStatus(callerUserId, id, request.status());
        return IncidentResponse.from(incident, incidentService.mediaFor(incident.getId()));
    }

    /**
     * Generates a PDF evidence pack for the incident and returns it as an attachment.
     * SUPERVISOR is intentionally excluded — the method-level annotation overrides the
     * class-level one. Only PROPERTY_MANAGER (scoped to their own property) and ADMIN.
     */
    @GetMapping("/{incidentId}/evidence-pack")
    @PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> evidencePack(Authentication authentication, @PathVariable Long incidentId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        byte[] pdf = evidencePackService.generatePack(callerUserId, incidentId);
        String filename = "evidence-pack-incident-" + incidentId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    /** Serves a photo's raw bytes — never as static content, always through this scoped, authenticated path. */
    @GetMapping("/{incidentId}/media/{mediaId}")
    public ResponseEntity<Resource> media(
            Authentication authentication, @PathVariable Long incidentId, @PathVariable Long mediaId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        IncidentMedia media = incidentService.getMedia(callerUserId, incidentId, mediaId);
        Resource resource = new FileSystemResource(Path.of(media.getFilePath()));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + media.getOriginalFilename() + "\"")
                .body(resource);
    }
}
