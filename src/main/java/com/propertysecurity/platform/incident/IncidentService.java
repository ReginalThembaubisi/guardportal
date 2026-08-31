package com.propertysecurity.platform.incident;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.incident.dto.IncidentMediaResponse;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import com.propertysecurity.platform.propertysupervisor.PropertySupervisorRepository;
import com.propertysecurity.platform.shift.Shift;
import com.propertysecurity.platform.shift.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * First pass on incident management (Phase 5, built ahead of pilot
 * feedback — see build_plan.md). Same open-shift requirement as
 * checkpoint scans (PatrolService.scan): a guard can't report an incident
 * while not clocked in. GPS is recorded, not verified against a reference
 * point — unlike shift/checkpoint, there's no natural "expected location"
 * for an incident to compare against.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentMediaRepository incidentMediaRepository;
    private final GuardRepository guardRepository;
    private final ShiftRepository shiftRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyManagerRepository propertyManagerRepository;
    private final PropertySupervisorRepository propertySupervisorRepository;
    private final AuditLogService auditLogService;

    @Value("${app.uploads.dir:./uploads}")
    private String uploadsDir;

    public Incident create(Long guardUserId, String description, IncidentSeverity severity,
                            BigDecimal latitude, BigDecimal longitude,
                            LocalDateTime clientClaimedAt, List<MultipartFile> photos) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNullFetchUser(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        Shift shift = shiftRepository.findByGuard_IdAndClockOutAtIsNull(guard.getId())
                .orElseThrow(() -> new BadRequestException("You must be clocked in to report an incident"));

        Incident incident = new Incident();
        incident.setProperty(guard.getProperty());
        incident.setReportedByGuard(guard);
        incident.setShift(shift);
        incident.setDescription(description);
        incident.setSeverity(severity);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setLatitude(latitude);
        incident.setLongitude(longitude);
        incident.setReportedAt(LocalDateTime.now());
        incident.setClientClaimedAt(clientClaimedAt);

        Incident saved = incidentRepository.save(incident);

        List<IncidentMedia> media = (photos == null ? List.<MultipartFile>of() : photos).stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> saveMedia(saved, file))
                .toList();

        auditLogService.record("incident", saved.getId(), AuditAction.CREATE, guardUserId, null, snapshot(saved, media));
        return saved;
    }

    /**
     * Scoped the same way as vehicle history/occupancy: a property
     * manager to properties they manage, a supervisor to properties they
     * supervise, ADMIN unrestricted. Either PM or supervisor rows can
     * satisfy the check — GUARD is not a viewer here (creation only, per
     * the endpoint's own scope).
     */
    @Transactional(readOnly = true)
    public List<Incident> listForProperty(Long callerUserId, Long propertyId) {
        if (propertyRepository.findByIdAndDeletedAtIsNull(propertyId).isEmpty()) {
            throw new ResourceNotFoundException("Property " + propertyId + " not found");
        }
        assertCanAccessProperty(callerUserId, propertyId);
        return incidentRepository.findAllByProperty_IdFetchDetails(propertyId);
    }

    @Transactional(readOnly = true)
    public Incident get(Long callerUserId, Long id) {
        Incident incident = incidentRepository.findByIdFetchDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident " + id + " not found"));
        assertCanAccessProperty(callerUserId, incident.getProperty().getId());
        return incident;
    }

    @Transactional(readOnly = true)
    public List<IncidentMediaResponse> mediaFor(Long incidentId) {
        return incidentMediaRepository.findAllByIncident_IdOrderByIdAsc(incidentId).stream()
                .map(IncidentMediaResponse::from)
                .toList();
    }

    /** Returns the on-disk path for a specific piece of media, scoped to its incident's property. */
    @Transactional(readOnly = true)
    public IncidentMedia getMedia(Long callerUserId, Long incidentId, Long mediaId) {
        Incident incident = get(callerUserId, incidentId);
        return incidentMediaRepository.findByIdAndIncident_Id(mediaId, incident.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Media " + mediaId + " not found"));
    }

    public Incident updateStatus(Long callerUserId, Long id, IncidentStatus newStatus) {
        Incident incident = incidentRepository.findByIdFetchDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident " + id + " not found"));
        assertCanAccessProperty(callerUserId, incident.getProperty().getId());

        IncidentStatus previousStatus = incident.getStatus();
        incident.setStatus(newStatus);
        Incident saved = incidentRepository.save(incident);

        auditLogService.record("incident", saved.getId(), AuditAction.UPDATE, callerUserId,
                Map.of("status", previousStatus), Map.of("status", newStatus));
        return saved;
    }

    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        boolean isAnyManager = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        boolean isAnySupervisor = propertySupervisorRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (!isAnyManager && !isAnySupervisor) {
            return; // ADMIN — unrestricted
        }
        boolean managesThis = isAnyManager
                && propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId);
        boolean supervisesThis = isAnySupervisor
                && propertySupervisorRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId);
        if (!managesThis && !supervisesThis) {
            throw new AccessDeniedException("This property is not yours");
        }
    }

    private IncidentMedia saveMedia(Incident incident, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Only image attachments are supported (got " + contentType + ")");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo";
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String storedFilename = UUID.randomUUID() + extension;

        try {
            Path incidentDir = Path.of(uploadsDir, "incidents", String.valueOf(incident.getId()));
            Files.createDirectories(incidentDir);
            Path target = incidentDir.resolve(storedFilename);
            file.transferTo(target);

            IncidentMedia media = new IncidentMedia();
            media.setIncident(incident);
            media.setFilePath(target.toString());
            media.setOriginalFilename(originalFilename);
            media.setContentType(contentType);
            media.setFileSizeBytes(file.getSize());
            return incidentMediaRepository.save(media);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store incident photo", e);
        }
    }

    private Map<String, Object> snapshot(Incident incident, List<IncidentMedia> media) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("propertyId", incident.getProperty().getId());
        map.put("reportedByGuardId", incident.getReportedByGuard().getId());
        map.put("shiftId", incident.getShift().getId());
        map.put("severity", incident.getSeverity());
        map.put("status", incident.getStatus());
        map.put("reportedAt", incident.getReportedAt());
        map.put("photoCount", media.size());
        return map;
    }
}
