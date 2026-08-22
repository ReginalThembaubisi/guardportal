package com.propertysecurity.platform.patrol.dto;

import java.time.LocalDateTime;
import java.util.List;

public record MissedCheckpointResponse(
        Long routeId,
        String routeName,
        LocalDateTime from,
        LocalDateTime to,
        List<CheckpointStatus> checkpoints
) {
    public record CheckpointStatus(
            Long checkpointId,
            String name,
            Integer sequenceOrder,
            boolean scanned,
            int scanCount,
            LocalDateTime firstScanAt,
            LocalDateTime lastScanAt
    ) {
    }
}
