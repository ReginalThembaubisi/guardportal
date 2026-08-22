package com.propertysecurity.platform.patrol.dto;

import com.propertysecurity.platform.patrol.PatrolRoute;
import com.propertysecurity.platform.patrol.PatrolRouteCheckpoint;

import java.time.LocalDateTime;
import java.util.List;

public record PatrolRouteResponse(
        Long id,
        Long propertyId,
        String name,
        List<CheckpointStop> checkpoints,
        LocalDateTime createdAt
) {
    public record CheckpointStop(Long checkpointId, String name, Integer sequenceOrder) {
    }

    public static PatrolRouteResponse from(PatrolRoute route, List<PatrolRouteCheckpoint> stops) {
        List<CheckpointStop> checkpoints = stops.stream()
                .map(stop -> new CheckpointStop(stop.getCheckpoint().getId(), stop.getCheckpoint().getName(), stop.getSequenceOrder()))
                .toList();
        return new PatrolRouteResponse(route.getId(), route.getProperty().getId(), route.getName(), checkpoints, route.getCreatedAt());
    }
}
