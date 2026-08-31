package com.propertysecurity.platform.patrol.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CheckpointScanRequest(
        @NotNull Long checkpointId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        /** Guard's phone clock at scan time; null when sent online. Server time is always authoritative. */
        LocalDateTime clientClaimedAt
) {
}
