package com.propertysecurity.platform.propertymanager;

import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertymanager.dto.PropertyManagerLinkRequest;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Links an existing PROPERTY_MANAGER-role staff account (created via
 * StaffController) to a property. Not a user-creation flow itself — that's
 * already covered by /api/v1/staff.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PropertyManagerService {

    private final PropertyManagerRepository propertyManagerRepository;
    private final AppUserRepository appUserRepository;
    private final PropertyRepository propertyRepository;

    public PropertyManager link(PropertyManagerLinkRequest request) {
        AppUser user = appUserRepository.findById(request.userId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User " + request.userId() + " not found"));
        if (!user.getRoles().contains(Role.PROPERTY_MANAGER)) {
            throw new BadRequestException("User " + request.userId() + " does not have the PROPERTY_MANAGER role");
        }

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));

        if (propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(user.getId(), property.getId())) {
            throw new ConflictException("This user already manages this property");
        }

        PropertyManager link = new PropertyManager();
        link.setUser(user);
        link.setProperty(property);
        return propertyManagerRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<PropertyManager> managedProperties(Long userId) {
        return propertyManagerRepository.findAllByUser_IdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public boolean managesProperty(Long userId, Long propertyId) {
        return propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(userId, propertyId);
    }
}
