package com.propertysecurity.platform.resident;

import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.resident.dto.ResidentRequest;
import com.propertysecurity.platform.unit.PropertyUnit;
import com.propertysecurity.platform.unit.PropertyUnitRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ResidentService {

    private final ResidentRepository residentRepository;
    private final PropertyUnitRepository unitRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public Resident create(ResidentRequest request) {
        PropertyUnit unit = unitRepository.findByIdAndDeletedAtIsNull(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit " + request.unitId() + " not found"));

        if (appUserRepository.existsByPhoneNumberAndDeletedAtIsNull(request.phoneNumber())) {
            throw new ConflictException("A user with phone number " + request.phoneNumber() + " already exists");
        }
        if (request.email() != null && appUserRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new ConflictException("A user with email " + request.email() + " already exists");
        }

        AppUser user = new AppUser();
        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setEmail(request.email());
        // Residents authenticate via phone+OTP, never a password. This hash
        // is a random, unusable placeholder to satisfy the NOT NULL column.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRoles(Set.of(Role.RESIDENT));
        appUserRepository.save(user);

        Resident resident = new Resident();
        resident.setUser(user);
        resident.setUnit(unit);
        return residentRepository.save(resident);
    }

    @Transactional(readOnly = true)
    public List<Resident> listAll() {
        return residentRepository.findAllByDeletedAtIsNull();
    }

    @Transactional(readOnly = true)
    public Resident get(Long id) {
        return residentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident " + id + " not found"));
    }

    public void softDelete(Long id) {
        Resident resident = get(id);
        resident.setDeletedAt(LocalDateTime.now());
        residentRepository.save(resident);
    }
}
