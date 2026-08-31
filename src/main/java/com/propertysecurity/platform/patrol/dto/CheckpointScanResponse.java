package com.propertysecurity.platform.patrol.dto;

import com.propertysecurity.platform.patrol.CheckpointScan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CheckpointScanResponse(
        Long id,
        Long checkpointId,
        String checkpointName,
        Long shiftId,
        Long guardId,
        LocalDateTime scannedAt,
        LocalDateTime clientClaimedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer distanceMeters,
        Boolean withinTolerance,
        LocalDateTime createdAt
) {
    public static CheckpointScanResponse from(CheckpointScan scan) {
        return new CheckpointScanResponse(
                scan.getId(),
                scan.getCheckpoint().getId(),
                scan.getCheckpoint().getName(),
                scan.getShift().getId(),
                scan.getGuard().getId(),
                scan.getScannedAt(),
                scan.getClientClaimedAt(),
                scan.getLatitude(),
                scan.getLongitude(),
                scan.getDistanceMeters(),
                scan.getWithinTolerance(),
                scan.getCreatedAt());
    }
}
