package com.propertysecurity.platform.guard;

import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.dto.GuardRequest;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class GuardService {

    private final GuardRepository guardRepository;
    private final PropertyRepository propertyRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public Guard create(GuardRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));

        if (appUserRepository.existsByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())) {
            throw new ConflictException("A user with phone number " + request.phoneNumber() + " already exists");
        }
        if (appUserRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new ConflictException("A user with email " + request.email() + " already exists");
        }

        AppUser user = new AppUser();
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(Set.of(Role.GUARD));
        appUserRepository.save(user);

        Guard guard = new Guard();
        guard.setUser(user);
        guard.setProperty(property);
        guard.setBadgeNumber(request.badgeNumber());
        return guardRepository.save(guard);
    }

    @Transactional(readOnly = true)
    public List<Guard> listAll() {
        return guardRepository.findAllByDeletedAtIsNull();
    }

    @Transactional(readOnly = true)
    public Guard get(Long id) {
        return guardRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Guard " + id + " not found"));
    }

    public void softDelete(Long id) {
        Guard guard = get(id);
        guard.setDeletedAt(LocalDateTime.now());
        guardRepository.save(guard);
    }
}
