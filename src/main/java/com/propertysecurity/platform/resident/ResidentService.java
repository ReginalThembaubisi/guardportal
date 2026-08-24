package com.propertysecurity.platform.resident;

import com.propertysecurity.platform.exception.ConflictException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertyclient.PropertyClientRepository;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import com.propertysecurity.platform.resident.dto.ResidentImportRequest;
import com.propertysecurity.platform.resident.dto.ResidentImportResponse;
import com.propertysecurity.platform.resident.dto.ResidentImportResultRow;
import com.propertysecurity.platform.resident.dto.ResidentImportRow;
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
import java.util.ArrayList;
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
    private final PropertyRepository propertyRepository;
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
     * Bulk onboarding for an existing residence (e.g. a few hundred/thousand
     * residents at once) — one call per row instead of the one-at-a-time
     * create() form. Deliberately never fails the whole batch over one bad
     * row: each row is validated independently and reported back as
     * created/skipped-with-reason, so a typo in row 400 of 1000 doesn't cost
     * the other 999. Units named in the CSV that don't exist yet on this
     * property are created on the fly — the whole point is this runs before
     * anyone has had a chance to enter units one by one.
     */
    public ResidentImportResponse importResidents(Long callerUserId, ResidentImportRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));
        assertCanAccessProperty(callerUserId, property.getId());

        List<ResidentImportResultRow> results = new ArrayList<>();
        int created = 0;
        int rowNumber = 0;
        for (ResidentImportRow row : request.rows()) {
            rowNumber++;
            String reason = validateImportRow(row);
            if (reason != null) {
                results.add(new ResidentImportResultRow(rowNumber, row.unitNumber(), row.fullName(), false, reason));
                continue;
            }

            PropertyUnit unit = unitRepository.findByProperty_IdAndUnitNumberAndDeletedAtIsNull(property.getId(), row.unitNumber())
                    .orElseGet(() -> {
                        PropertyUnit newUnit = new PropertyUnit();
                        newUnit.setProperty(property);
                        newUnit.setUnitNumber(row.unitNumber());
                        return unitRepository.save(newUnit);
                    });

            AppUser user = new AppUser();
            user.setFullName(row.fullName());
            user.setPhoneNumber(row.phoneNumber());
            // Blank -> null, never "": an empty-string email would collide
            // with every other row that also left it blank, since unlike
            // NULL, MySQL treats "" as a real (and here, unique-constrained)
            // value.
            user.setEmail(row.email() != null && !row.email().isBlank() ? row.email() : null);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRoles(Set.of(Role.RESIDENT));
            appUserRepository.save(user);

            Resident resident = new Resident();
            resident.setUser(user);
            resident.setUnit(unit);
            residentRepository.save(resident);

            created++;
            results.add(new ResidentImportResultRow(rowNumber, row.unitNumber(), row.fullName(), true, null));
        }

        return new ResidentImportResponse(created, results.size() - created, results);
    }

    private String validateImportRow(ResidentImportRow row) {
        if (row.unitNumber() == null || row.unitNumber().isBlank()) {
            return "Missing unit number";
        }
        if (row.fullName() == null || row.fullName().isBlank()) {
            return "Missing name";
        }
        if (row.phoneNumber() == null || row.phoneNumber().isBlank()) {
            return "Missing phone number";
        }
        if (appUserRepository.existsByPhoneNumberAndDeletedAtIsNull(row.phoneNumber())) {
            return "Phone number " + row.phoneNumber() + " already in use";
        }
        if (row.email() != null && !row.email().isBlank() && appUserRepository.existsByEmailAndDeletedAtIsNull(row.email())) {
            return "Email " + row.email() + " already in use";
        }
        return null;
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
