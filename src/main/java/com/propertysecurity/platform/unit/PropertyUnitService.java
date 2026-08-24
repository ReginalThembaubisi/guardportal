package com.propertysecurity.platform.unit;

import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import com.propertysecurity.platform.unit.dto.UnitRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyUnitService {

    private final PropertyUnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyManagerRepository propertyManagerRepository;
    private final GuardRepository guardRepository;

    public PropertyUnit create(Long propertyId, UnitRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property " + propertyId + " not found"));

        if (unitRepository.existsByProperty_IdAndUnitNumberAndDeletedAtIsNull(propertyId, request.unitNumber())) {
            throw new ConflictException("Unit " + request.unitNumber() + " already exists on this property");
        }

        PropertyUnit unit = new PropertyUnit();
        unit.setProperty(property);
        unit.setUnitNumber(request.unitNumber());
        return unitRepository.save(unit);
    }

    @Transactional(readOnly = true)
    public List<PropertyUnit> listByProperty(Long propertyId) {
        return unitRepository.findAllByProperty_IdAndDeletedAtIsNull(propertyId);
    }

    /** Read, scoped to a caller — see UnitReadController. Property managers can only browse their own properties' units; ADMIN is unrestricted. */
    @Transactional(readOnly = true)
    public List<PropertyUnit> listByPropertyForCaller(Long callerUserId, Long propertyId) {
        if (propertyRepository.findByIdAndDeletedAtIsNull(propertyId).isEmpty()) {
            throw new ResourceNotFoundException("Property " + propertyId + " not found");
        }
        assertCanAccessProperty(callerUserId, propertyId);
        return listByProperty(propertyId);
    }

    @Transactional(readOnly = true)
    public PropertyUnit get(Long id) {
        return unitRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit " + id + " not found"));
    }

    /** Read, scoped to a caller — see UnitReadController. */
    @Transactional(readOnly = true)
    public PropertyUnit getForCaller(Long callerUserId, Long id) {
        PropertyUnit unit = get(id);
        assertCanAccessProperty(callerUserId, unit.getProperty().getId());
        return unit;
    }

    /**
     * Same idiom as VisitorEntryService.assertCanAccessProperty: a guard is
     * restricted to their own assigned property; a caller with no
     * PropertyManager rows at all (i.e. ADMIN, the only other role that
     * reaches this) is unrestricted; a caller with any PropertyManager rows
     * must have one matching this property.
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

    public PropertyUnit update(Long id, UnitRequest request) {
        PropertyUnit unit = get(id);
        unit.setUnitNumber(request.unitNumber());
        return unitRepository.save(unit);
    }

    public void softDelete(Long id) {
        PropertyUnit unit = get(id);
        unit.setDeletedAt(LocalDateTime.now());
        unitRepository.save(unit);
    }
}
