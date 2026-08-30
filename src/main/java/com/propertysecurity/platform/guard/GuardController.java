package com.propertysecurity.platform.guard;

import com.propertysecurity.platform.guard.dto.GuardRequest;
import com.propertysecurity.platform.guard.dto.GuardResponse;
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
@RequestMapping("/api/v1/guards")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public class GuardController {

    private final GuardService guardService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuardResponse create(Authentication authentication, @Valid @RequestBody GuardRequest request) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return GuardResponse.from(guardService.create(callerUserId, request));
    }

    @GetMapping
    public List<GuardResponse> listAll() {
        return guardService.listAll().stream().map(GuardResponse::from).toList();
    }

    @GetMapping("/{id}")
    public GuardResponse get(@PathVariable Long id) {
        return GuardResponse.from(guardService.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        guardService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
