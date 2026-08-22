package com.propertysecurity.platform.unit;

import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.unit.dto.UnitRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyUnitService {

    private final PropertyUnitRepository unitRepository;
    private final PropertyRepository propertyRepository;

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

    @Transactional(readOnly = true)
    public PropertyUnit get(Long id) {
        return unitRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit " + id + " not found"));
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
