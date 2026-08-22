package com.propertysecurity.platform.property;

import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.property.dto.PropertyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public Property create(PropertyRequest request) {
        Property property = new Property();
        property.setName(request.name());
        property.setAddress(request.address());
        if (request.timezone() != null && !request.timezone().isBlank()) {
            property.setTimezone(request.timezone());
        }
        property.setLatitude(request.latitude());
        property.setLongitude(request.longitude());
        property.setGeoToleranceMeters(request.geoToleranceMeters());
        return propertyRepository.save(property);
    }

    @Transactional(readOnly = true)
    public List<Property> listAll() {
        return propertyRepository.findAllByDeletedAtIsNull();
    }

    @Transactional(readOnly = true)
    public Property get(Long id) {
        return propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property " + id + " not found"));
    }

    public Property update(Long id, PropertyRequest request) {
        Property property = get(id);
        property.setName(request.name());
        property.setAddress(request.address());
        if (request.timezone() != null && !request.timezone().isBlank()) {
            property.setTimezone(request.timezone());
        }
        property.setLatitude(request.latitude());
        property.setLongitude(request.longitude());
        property.setGeoToleranceMeters(request.geoToleranceMeters());
        return propertyRepository.save(property);
    }

    public void softDelete(Long id) {
        Property property = get(id);
        property.setDeletedAt(LocalDateTime.now());
        propertyRepository.save(property);
    }
}
