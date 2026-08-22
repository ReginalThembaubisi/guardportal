package com.propertysecurity.platform.checkpoint;

import com.propertysecurity.platform.checkpoint.dto.CheckpointRequest;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.invitation.QrCodeGenerator;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Checkpoints are config/master data (like guard, property_manager) — set
 * up once, patrolled against many times. Not audited, same as those:
 * CLAUDE.md rule 2 covers visitor_entry/invitation/vehicle (per-event
 * records); the actual per-event record here is checkpoint_scan, which
 * PatrolService does audit.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CheckpointService {

    private final CheckpointRepository checkpointRepository;
    private final PropertyRepository propertyRepository;
    private final QrCodeGenerator qrCodeGenerator;

    public record Created(Checkpoint checkpoint, String qrCodeDataUri) {
    }

    public Created create(CheckpointRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setProperty(property);
        checkpoint.setName(request.name());
        checkpoint.setLatitude(request.latitude());
        checkpoint.setLongitude(request.longitude());
        checkpoint.setGeoToleranceMeters(request.geoToleranceMeters());
        checkpoint.setQrToken(UUID.randomUUID().toString());

        Checkpoint saved = checkpointRepository.save(checkpoint);
        // Same mechanism as invitation QR codes (server-side ZXing) — but the
        // scanned content is just the raw token, not a URL: unlike an
        // invitation link, nothing renders a web page for a scanned
        // checkpoint. The guard app reads/types the token directly into the
        // scan endpoint.
        String qrCodeDataUri = qrCodeGenerator.generatePngDataUri(saved.getQrToken());
        return new Created(saved, qrCodeDataUri);
    }

    @Transactional(readOnly = true)
    public Checkpoint get(Long id) {
        return checkpointRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint " + id + " not found"));
    }

    /** Regenerates the QR image for an existing checkpoint. Pure, nothing persisted. */
    @Transactional(readOnly = true)
    public Created getShareable(Long id) {
        Checkpoint checkpoint = get(id);
        return new Created(checkpoint, qrCodeGenerator.generatePngDataUri(checkpoint.getQrToken()));
    }
}
