package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.checkpoint.Checkpoint;
import com.propertysecurity.platform.checkpoint.CheckpointRepository;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.patrol.dto.PatrolRouteRequest;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public record Created(PatrolRoute route, List<PatrolRouteCheckpoint> stops) {
    }

    public Created create(PatrolRouteRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));

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
}
