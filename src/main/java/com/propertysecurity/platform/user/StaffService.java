package com.propertysecurity.platform.user;

import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.user.dto.StaffRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Creates non-resident, non-guard staff accounts (ADMIN, PROPERTY_MANAGER,
 * SUPERVISOR, CLIENT). Residents and guards have their own dedicated create
 * flows tied to a unit/property, so this is deliberately excluded here.
 *
 * Not called out explicitly in the Phase 1 build plan, but needed to
 * bootstrap property manager / supervisor / client / additional admin
 * accounts so "log in as each role" is actually testable.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StaffService {

    private static final Set<Role> ALLOWED_ROLES = Set.of(
            Role.ADMIN, Role.PROPERTY_MANAGER, Role.SUPERVISOR, Role.CLIENT);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUser create(StaffRequest request) {
        if (!ALLOWED_ROLES.contains(request.role())) {
            throw new BadRequestException(
                    "Role must be one of " + ALLOWED_ROLES + " (residents and guards have their own create endpoints)");
        }
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
        user.setRoles(Set.of(request.role()));
        return appUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<AppUser> listAll() {
        return appUserRepository.findAllByDeletedAtIsNull();
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return appUserRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }
}
