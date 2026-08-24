package com.propertysecurity.platform.resident;

import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.propertyclient.PropertyClientRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import com.propertysecurity.platform.resident.dto.ResidentRequest;
import com.propertysecurity.platform.unit.PropertyUnit;
import com.propertysecurity.platform.unit.PropertyUnitRepository;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ResidentService {

    private final ResidentRepository residentRepository;
    private final PropertyUnitRepository unitRepository;
    private final AppUserRepository appUserRepository;
    private final PropertyManagerRepository propertyManagerRepository;
    private final PropertyClientRepository propertyClientRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * callerUserId is who's making the request (a property manager, client,
     * or ADMIN — enforced at the controller). Same assertCanAccessProperty
     * idiom as VisitorEntryService/PropertyUnitService: a caller who is
     * neither a property manager nor a client (i.e. ADMIN) is unrestricted;
     * a caller who is one or the other must have a matching link row.
     */
    public Resident create(Long callerUserId, ResidentRequest request) {
        PropertyUnit unit = unitRepository.findByIdAndDeletedAtIsNull(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException("Unit " + request.unitId() + " not found"));

        assertCanAccessProperty(callerUserId, unit.getProperty().getId());

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

    /**
     * Scoped to the caller: a property manager or client only sees residents
     * on properties they're linked to; ADMIN (neither) is unrestricted.
     * Previously unscoped entirely — any PM/CLIENT could list every
     * resident on every property.
     */
    @Transactional(readOnly = true)
    public List<Resident> listForCaller(Long callerUserId) {
        List<Long> managedPropertyIds = propertyManagerRepository.findAllByUser_IdAndDeletedAtIsNull(callerUserId)
                .stream().map(link -> link.getProperty().getId()).collect(Collectors.toList());
        List<Long> ownedPropertyIds = propertyClientRepository.findAllByUser_IdAndDeletedAtIsNull(callerUserId)
                .stream().map(link -> link.getProperty().getId()).toList();
        managedPropertyIds.addAll(ownedPropertyIds);

        boolean isScoped = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId)
                || propertyClientRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (!isScoped) {
            return residentRepository.findAllByDeletedAtIsNull();
        }
        if (managedPropertyIds.isEmpty()) {
            return List.of();
        }
        return residentRepository.findAllByUnit_Property_IdInAndDeletedAtIsNull(managedPropertyIds);
    }

    @Transactional(readOnly = true)
    public Resident getForCaller(Long callerUserId, Long id) {
        Resident resident = residentRepository.findByIdFetchUnitAndProperty(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident " + id + " not found"));
        assertCanAccessProperty(callerUserId, resident.getUnit().getProperty().getId());
        return resident;
    }

    public void softDeleteForCaller(Long callerUserId, Long id) {
        Resident resident = residentRepository.findByIdFetchUnitAndProperty(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident " + id + " not found"));
        assertCanAccessProperty(callerUserId, resident.getUnit().getProperty().getId());
        resident.setDeletedAt(LocalDateTime.now());
        residentRepository.save(resident);
    }

    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        boolean isAnyPropertyManager = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnyPropertyManager) {
            if (!propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
                throw new AccessDeniedException("This property is not yours");
            }
            return;
        }

        boolean isAnyClient = propertyClientRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnyClient && !propertyClientRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
    }
}
