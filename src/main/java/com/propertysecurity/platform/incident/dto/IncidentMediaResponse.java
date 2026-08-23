package com.propertysecurity.platform.incident.dto;

import com.propertysecurity.platform.incident.IncidentMedia;

import java.time.LocalDateTime;

/** file_path is deliberately not exposed — media is fetched through IncidentController.media, not a raw path. */
public record IncidentMediaResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        LocalDateTime createdAt
) {
    public static IncidentMediaResponse from(IncidentMedia media) {
        return new IncidentMediaResponse(
                media.getId(),
                media.getOriginalFilename(),
                media.getContentType(),
                media.getFileSizeBytes(),
                media.getCreatedAt());
    }
}
