package com.propertysecurity.platform.checkpoint;

import com.propertysecurity.platform.checkpoint.dto.CheckpointRequest;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.invitation.QrCodeGenerator;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final PropertyManagerRepository propertyManagerRepository;
    private final QrCodeGenerator qrCodeGenerator;

    public record Created(Checkpoint checkpoint, String qrCodeDataUri) {
    }

    /**
     * callerUserId is who's making the request (a property manager or
     * ADMIN — enforced at the controller). Previously accepted any
     * propertyId with no check the caller manages it — same
     * assertCanAccessProperty idiom as PropertyUnitService/ResidentService.
     */
    public Created create(Long callerUserId, CheckpointRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));
        assertCanAccessProperty(callerUserId, request.propertyId());

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

    /** Scoped read — needed to pick checkpoints when building a patrol route. */
    @Transactional(readOnly = true)
    public List<Checkpoint> listByPropertyForCaller(Long callerUserId, Long propertyId) {
        if (propertyRepository.findByIdAndDeletedAtIsNull(propertyId).isEmpty()) {
            throw new ResourceNotFoundException("Property " + propertyId + " not found");
        }
        assertCanAccessProperty(callerUserId, propertyId);
        return checkpointRepository.findAllByProperty_IdAndDeletedAtIsNull(propertyId);
    }

    /** Same idiom as PropertyUnitService.assertCanAccessProperty. */
    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        boolean isAnyPropertyManager = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnyPropertyManager && !propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
    }
}
