package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.checkpoint.Checkpoint;
import com.propertysecurity.platform.checkpoint.CheckpointRepository;
import com.propertysecurity.platform.common.GeoDistance;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.patrol.dto.CheckpointScanRequest;
import com.propertysecurity.platform.patrol.dto.MissedCheckpointResponse;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import com.propertysecurity.platform.shift.Shift;
import com.propertysecurity.platform.shift.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The operational half of patrols (checkpoint config/route composition
 * lives in CheckpointService/PatrolRouteService): scanning a checkpoint,
 * and reporting which were/weren't scanned. Same first-pass GPS reasoning
 * as ShiftService — flagged, never blocked.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PatrolService {

    private final CheckpointRepository checkpointRepository;
    private final GuardRepository guardRepository;
    private final ShiftRepository shiftRepository;
    private final PatrolRouteRepository patrolRouteRepository;
    private final PatrolRouteCheckpointRepository patrolRouteCheckpointRepository;
    private final CheckpointScanRepository checkpointScanRepository;
    private final PropertyManagerRepository propertyManagerRepository;
    private final AuditLogService auditLogService;

    @Value("${app.geo.default-tolerance-meters:150}")
    private int defaultToleranceMeters;

    public CheckpointScan scan(Long guardUserId, CheckpointScanRequest request) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        Checkpoint checkpoint = checkpointRepository.findByIdAndDeletedAtIsNull(request.checkpointId())
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));

        if (!checkpoint.getProperty().getId().equals(guard.getProperty().getId())) {
            throw new AccessDeniedException("This checkpoint is for a different property");
        }

        Shift shift = shiftRepository.findByGuard_IdAndClockOutAtIsNull(guard.getId())
                .orElseThrow(() -> new BadRequestException("You must be clocked in to check in at a checkpoint"));

        int tolerance = tolerance(checkpoint);
        int distance = GeoDistance.metersBetween(
                checkpoint.getLatitude(), checkpoint.getLongitude(), request.latitude(), request.longitude());

        CheckpointScan scan = new CheckpointScan();
        scan.setCheckpoint(checkpoint);
        scan.setShift(shift);
        scan.setGuard(guard);
        scan.setScannedAt(LocalDateTime.now());
        scan.setClientClaimedAt(request.clientClaimedAt());
        scan.setLatitude(request.latitude());
        scan.setLongitude(request.longitude());
        scan.setDistanceMeters(distance);
        scan.setWithinTolerance(distance <= tolerance);

        CheckpointScan saved = checkpointScanRepository.save(scan);
        auditLogService.record("checkpoint_scan", saved.getId(), AuditAction.CREATE, guardUserId, null, snapshot(saved));
        return saved;
    }

    /**
     * Scoped the same way as occupancy/vehicle history: a guard sees only
     * their own property's routes, a property manager only routes on
     * properties they manage, ADMIN unrestricted. SUPERVISOR still has no
     * property association in the schema (see VisitorEntryService).
     */
    @Transactional(readOnly = true)
    public MissedCheckpointResponse missedCheckpoints(Long callerUserId, Long routeId, LocalDateTime from, LocalDateTime to) {
        if (!to.isAfter(from)) {
            throw new BadRequestException("'to' must be after 'from'");
        }

        PatrolRoute route = patrolRouteRepository.findByIdAndDeletedAtIsNull(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Patrol route " + routeId + " not found"));
        assertCanAccessProperty(callerUserId, route.getProperty().getId());

        List<PatrolRouteCheckpoint> stops = patrolRouteCheckpointRepository.findAllByRouteIdOrderBySequence(routeId);
        List<Long> checkpointIds = stops.stream().map(stop -> stop.getCheckpoint().getId()).toList();

        List<CheckpointScan> scans = checkpointIds.isEmpty()
                ? List.of()
                : checkpointScanRepository.findAllByCheckpointIdInAndScannedAtBetween(checkpointIds, from, to);

        Map<Long, List<CheckpointScan>> scansByCheckpoint = new LinkedHashMap<>();
        for (CheckpointScan scan : scans) {
            scansByCheckpoint.computeIfAbsent(scan.getCheckpoint().getId(), id -> new java.util.ArrayList<>()).add(scan);
        }

        List<MissedCheckpointResponse.CheckpointStatus> statuses = stops.stream()
                .map(stop -> {
                    List<CheckpointScan> checkpointScans = scansByCheckpoint.getOrDefault(stop.getCheckpoint().getId(), List.of());
                    LocalDateTime first = checkpointScans.isEmpty() ? null : checkpointScans.get(0).getScannedAt();
                    CheckpointScan lastScan = checkpointScans.isEmpty() ? null : checkpointScans.get(checkpointScans.size() - 1);
                    return new MissedCheckpointResponse.CheckpointStatus(
                            stop.getCheckpoint().getId(), stop.getCheckpoint().getName(), stop.getSequenceOrder(),
                            !checkpointScans.isEmpty(), checkpointScans.size(),
                            first, lastScan != null ? lastScan.getScannedAt() : null,
                            lastScan != null ? lastScan.getDistanceMeters() : null,
                            lastScan != null ? lastScan.getWithinTolerance() : null);
                })
                .toList();

        return new MissedCheckpointResponse(route.getId(), route.getName(), from, to, statuses);
    }

    private int tolerance(Checkpoint checkpoint) {
        if (checkpoint.getGeoToleranceMeters() != null) {
            return checkpoint.getGeoToleranceMeters();
        }
        Property property = checkpoint.getProperty();
        return property.getGeoToleranceMeters() != null ? property.getGeoToleranceMeters() : defaultToleranceMeters;
    }

    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        Optional<Guard> guard = guardRepository.findByUser_IdAndDeletedAtIsNull(callerUserId);
        if (guard.isPresent()) {
            if (!guard.get().getProperty().getId().equals(propertyId)) {
                throw new AccessDeniedException("This property is not yours");
            }
            return;
        }

        boolean isAnyPropertyManager = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnyPropertyManager && !propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
    }

    private Map<String, Object> snapshot(CheckpointScan scan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("checkpointId", scan.getCheckpoint().getId());
        map.put("shiftId", scan.getShift().getId());
        map.put("guardId", scan.getGuard().getId());
        map.put("scannedAt", scan.getScannedAt());
        map.put("distanceMeters", scan.getDistanceMeters());
        map.put("withinTolerance", scan.getWithinTolerance());
        return map;
    }
}
