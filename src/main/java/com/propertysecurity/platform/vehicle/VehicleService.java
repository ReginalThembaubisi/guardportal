package com.propertysecurity.platform.vehicle;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.resident.Resident;
import com.propertysecurity.platform.resident.ResidentRepository;
import com.propertysecurity.platform.vehicle.dto.VehicleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ResidentVehicleRepository residentVehicleRepository;
    private final ResidentRepository residentRepository;
    private final AuditLogService auditLogService;

    /**
     * Find-or-create by registration. Only the CREATE path writes an
     * audit_log row (CLAUDE.md rule 2 names vehicle explicitly) — reusing
     * an existing vehicle isn't a write to it.
     */
    public Vehicle findOrCreate(String registration, String make, String model, String colour, Long performedByUserId) {
        String normalized = normalize(registration);
        return vehicleRepository.findByRegistration(normalized)
                .orElseGet(() -> create(normalized, make, model, colour, performedByUserId));
    }

    private Vehicle create(String registration, String make, String model, String colour, Long performedByUserId) {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistration(registration);
        vehicle.setMake(make);
        vehicle.setModel(model);
        vehicle.setColour(colour);
        Vehicle saved = vehicleRepository.save(vehicle);

        auditLogService.record("vehicle", saved.getId(), AuditAction.CREATE, performedByUserId, null, snapshot(saved));
        return saved;
    }

    /** Links a vehicle (find-or-create by registration) to the caller's own resident record. */
    public ResidentVehicle registerForResident(Long residentUserId, VehicleRequest request) {
        Resident resident = residentRepository.findByUser_IdAndDeletedAtIsNull(residentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No resident profile found for this account"));

        // Reusing an existing vehicle deliberately doesn't overwrite its make/model/colour —
        // it's a shared record keyed by registration, and one resident's request shouldn't
        // silently clobber details another resident (or a guard, at check-in) already set.
        Vehicle vehicle = findOrCreate(request.registration(), request.make(), request.model(), request.colour(), residentUserId);

        if (residentVehicleRepository.existsByResident_IdAndVehicle_Id(resident.getId(), vehicle.getId())) {
            // Already linked — return an in-memory representation built from the
            // resident/vehicle objects already loaded above, rather than re-fetching
            // by ID (which would come back with a lazy, uninitialized vehicle proxy
            // that blows up the moment the controller reads a field off it).
            ResidentVehicle existing = new ResidentVehicle();
            existing.setId(new ResidentVehicleId(resident.getId(), vehicle.getId()));
            existing.setResident(resident);
            existing.setVehicle(vehicle);
            return existing;
        }

        ResidentVehicle link = new ResidentVehicle();
        link.setId(new ResidentVehicleId(resident.getId(), vehicle.getId()));
        link.setResident(resident);
        link.setVehicle(vehicle);
        return residentVehicleRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> listForResident(Long residentUserId) {
        Resident resident = residentRepository.findByUser_IdAndDeletedAtIsNull(residentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No resident profile found for this account"));
        return residentVehicleRepository.findAllByResident_Id(resident.getId()).stream()
                .map(ResidentVehicle::getVehicle)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isRecognized(Long vehicleId) {
        return vehicleId != null && residentVehicleRepository.existsByVehicle_Id(vehicleId);
    }

    private String normalize(String registration) {
        return registration.trim().toUpperCase();
    }

    private Map<String, Object> snapshot(Vehicle vehicle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("registration", vehicle.getRegistration());
        map.put("make", vehicle.getMake());
        map.put("model", vehicle.getModel());
        map.put("colour", vehicle.getColour());
        return map;
    }
}
