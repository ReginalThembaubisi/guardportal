package com.propertysecurity.platform.checkpoint;

import com.propertysecurity.platform.checkpoint.dto.CheckpointRequest;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
    private final GuardRepository guardRepository;

    /**
     * callerUserId is who's making the request (a property manager or
     * ADMIN — enforced at the controller). Previously accepted any
     * propertyId with no check the caller manages it — same
     * assertCanAccessProperty idiom as PropertyUnitService/ResidentService.
     */
    public Checkpoint create(Long callerUserId, CheckpointRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));
        assertCanAccessProperty(callerUserId, request.propertyId());

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setProperty(property);
        checkpoint.setName(request.name());
        checkpoint.setLatitude(request.latitude());
        checkpoint.setLongitude(request.longitude());
        checkpoint.setGeoToleranceMeters(request.geoToleranceMeters());

        return checkpointRepository.save(checkpoint);
    }

    @Transactional(readOnly = true)
    public Checkpoint get(Long id) {
        return checkpointRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint " + id + " not found"));
    }

    /**
     * Scoped read — needed to pick checkpoints when building a patrol route
     * (property manager), and by a guard to pick which checkpoint they're
     * checking into (no QR scan involved any more).
     */
    @Transactional(readOnly = true)
    public List<Checkpoint> listByPropertyForCaller(Long callerUserId, Long propertyId) {
        if (propertyRepository.findByIdAndDeletedAtIsNull(propertyId).isEmpty()) {
            throw new ResourceNotFoundException("Property " + propertyId + " not found");
        }
        assertCanAccessProperty(callerUserId, propertyId);
        return checkpointRepository.findAllByProperty_IdAndDeletedAtIsNull(propertyId);
    }

    /**
     * A guard caller must match the target property exactly (same idiom as
     * PatrolService.assertCanAccessProperty) — this used to silently permit
     * any non-property-manager caller through unchecked, which was harmless
     * only because guards were blocked at the controller level entirely.
     * Now that GUARD can call listByPropertyForCaller, this must actually
     * check them.
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
