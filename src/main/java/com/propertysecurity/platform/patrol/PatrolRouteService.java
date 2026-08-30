package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.checkpoint.Checkpoint;
import com.propertysecurity.platform.checkpoint.CheckpointRepository;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.patrol.dto.PatrolRouteRequest;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Route composition — not audited, same reasoning as CheckpointService. */
@Service
@RequiredArgsConstructor
@Transactional
public class PatrolRouteService {

    private final PatrolRouteRepository patrolRouteRepository;
    private final PatrolRouteCheckpointRepository patrolRouteCheckpointRepository;
    private final CheckpointRepository checkpointRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyManagerRepository propertyManagerRepository;
    private final GuardRepository guardRepository;

    public record Created(PatrolRoute route, List<PatrolRouteCheckpoint> stops) {
    }

    /**
     * callerUserId is who's making the request. Previously accepted any
     * propertyId with no check the caller manages it — same
     * assertCanAccessProperty idiom as CheckpointService/ResidentService.
     */
    public Created create(Long callerUserId, PatrolRouteRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));
        assertCanAccessProperty(callerUserId, request.propertyId());

        Set<Long> distinctIds = new HashSet<>(request.checkpointIds());
        if (distinctIds.size() != request.checkpointIds().size()) {
            throw new BadRequestException("checkpointIds cannot contain duplicates");
        }

        List<Checkpoint> checkpoints = checkpointRepository.findAllByIdInAndDeletedAtIsNull(request.checkpointIds());
        Map<Long, Checkpoint> byId = new HashMap<>();
        for (Checkpoint checkpoint : checkpoints) {
            if (!checkpoint.getProperty().getId().equals(property.getId())) {
                throw new BadRequestException("Checkpoint " + checkpoint.getId() + " does not belong to property " + property.getId());
            }
            byId.put(checkpoint.getId(), checkpoint);
        }
        for (Long checkpointId : request.checkpointIds()) {
            if (!byId.containsKey(checkpointId)) {
                throw new ResourceNotFoundException("Checkpoint " + checkpointId + " not found");
            }
        }

        PatrolRoute route = new PatrolRoute();
        route.setProperty(property);
        route.setName(request.name());
        PatrolRoute savedRoute = patrolRouteRepository.save(route);

        List<PatrolRouteCheckpoint> stops = new java.util.ArrayList<>();
        int sequence = 1;
        for (Long checkpointId : request.checkpointIds()) {
            PatrolRouteCheckpoint stop = new PatrolRouteCheckpoint();
            stop.setId(new PatrolRouteCheckpointId(savedRoute.getId(), checkpointId));
            stop.setRoute(savedRoute);
            stop.setCheckpoint(byId.get(checkpointId));
            stop.setSequenceOrder(sequence++);
            stops.add(patrolRouteCheckpointRepository.save(stop));
        }

        return new Created(savedRoute, stops);
    }

    @Transactional(readOnly = true)
    public PatrolRoute get(Long id) {
        return patrolRouteRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patrol route " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<PatrolRouteCheckpoint> stopsFor(Long routeId) {
        return patrolRouteCheckpointRepository.findAllByRouteIdOrderBySequence(routeId);
    }

    /** Scoped read — needed to pick a route when checking missed-checkpoint status. */
    @Transactional(readOnly = true)
    public List<PatrolRoute> listByPropertyForCaller(Long callerUserId, Long propertyId) {
        if (propertyRepository.findByIdAndDeletedAtIsNull(propertyId).isEmpty()) {
            throw new ResourceNotFoundException("Property " + propertyId + " not found");
        }
        assertCanAccessProperty(callerUserId, propertyId);
        return patrolRouteRepository.findAllByProperty_IdAndDeletedAtIsNull(propertyId);
    }

    /**
     * A guard caller must match the target property exactly (same idiom as
     * PatrolService.assertCanAccessProperty) — this used to silently permit
     * any non-property-manager caller through unchecked, harmless only
     * because GUARD was fully blocked at the controller level before it
     * needed to list routes for its own patrol-progress screen.
     */
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
}
