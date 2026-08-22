package com.propertysecurity.platform.resident;

import com.propertysecurity.platform.resident.dto.ResidentRequest;
import com.propertysecurity.platform.resident.dto.ResidentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/residents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'ADMIN')")
public class ResidentController {

    private final ResidentService residentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResidentResponse create(Authentication authentication, @Valid @RequestBody ResidentRequest request) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return ResidentResponse.from(residentService.create(callerUserId, request));
    }

    @GetMapping
    public List<ResidentResponse> listAll() {
        return residentService.listAll().stream().map(ResidentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResidentResponse get(@PathVariable Long id) {
        return ResidentResponse.from(residentService.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        residentService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
