package com.propertysecurity.platform.propertysupervisor;

import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertysupervisor.dto.PropertySupervisorLinkRequest;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Links an existing SUPERVISOR-role staff account (created via
 * StaffController) to a property. Mirrors PropertyManagerService exactly.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PropertySupervisorService {

    private final PropertySupervisorRepository propertySupervisorRepository;
    private final AppUserRepository appUserRepository;
    private final PropertyRepository propertyRepository;

    public PropertySupervisor link(PropertySupervisorLinkRequest request) {
        AppUser user = appUserRepository.findById(request.userId())
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User " + request.userId() + " not found"));
        if (!user.getRoles().contains(Role.SUPERVISOR)) {
            throw new BadRequestException("User " + request.userId() + " does not have the SUPERVISOR role");
        }

        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));

        if (propertySupervisorRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(user.getId(), property.getId())) {
            throw new ConflictException("This user already supervises this property");
        }

        PropertySupervisor link = new PropertySupervisor();
        link.setUser(user);
        link.setProperty(property);
        return propertySupervisorRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<PropertySupervisor> supervisedProperties(Long userId) {
        return propertySupervisorRepository.findAllByUser_IdAndDeletedAtIsNull(userId);
    }
}
