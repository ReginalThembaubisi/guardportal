package com.propertysecurity.platform.propertyclient;

import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertyclient.dto.PropertyClientLinkRequest;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Links an existing CLIENT-role staff account (created via StaffController)
 * to a property they own. Not a user-creation flow itself — that's already
 * covered by /api/v1/staff. Mirrors PropertyManagerService exactly.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PropertyClientService {

    private final PropertyClientRepository propertyClientRepository;
    private final AppUserRepository appUserRepository;
    private final PropertyRepository propertyRepository;

    public PropertyClient link(PropertyClientLinkRequest request) {
        AppUser user = appUserRepository.findById(request.userId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User " + request.userId() + " not found"));
        if (!user.getRoles().contains(Role.CLIENT)) {
            throw new BadRequestException("User " + request.userId() + " does not have the CLIENT role");
        }

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));

        if (propertyClientRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(user.getId(), property.getId())) {
            throw new ConflictException("This user already owns this property");
        }

        PropertyClient link = new PropertyClient();
        link.setUser(user);
        link.setProperty(property);
        return propertyClientRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<PropertyClient> ownedProperties(Long userId) {
        return propertyClientRepository.findAllByUser_IdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public boolean ownsProperty(Long userId, Long propertyId) {
        return propertyClientRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(userId, propertyId);
    }
}
