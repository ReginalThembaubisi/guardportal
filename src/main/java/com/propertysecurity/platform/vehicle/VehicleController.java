package com.propertysecurity.platform.vehicle;

import com.propertysecurity.platform.vehicle.dto.VehicleRequest;
import com.propertysecurity.platform.vehicle.dto.VehicleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class VehicleController {

    private final VehicleService vehicleService;

    /** Finds-or-creates the vehicle by registration and links it to the caller's own resident record. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse register(Authentication authentication, @Valid @RequestBody VehicleRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return VehicleResponse.from(vehicleService.registerForResident(userId, request).getVehicle());
    }

    @GetMapping
    public List<VehicleResponse> listOwn(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return vehicleService.listForResident(userId).stream().map(VehicleResponse::from).toList();
    }
}
