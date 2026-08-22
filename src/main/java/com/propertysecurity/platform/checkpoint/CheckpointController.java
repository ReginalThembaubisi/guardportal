package com.propertysecurity.platform.checkpoint;

import com.propertysecurity.platform.checkpoint.dto.CheckpointRequest;
import com.propertysecurity.platform.checkpoint.dto.CheckpointResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checkpoints")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PROPERTY_MANAGER', 'ADMIN')")
public class CheckpointController {

    private final CheckpointService checkpointService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckpointResponse create(Authentication authentication, @Valid @RequestBody CheckpointRequest request) {
        Long callerUserId = (Long) authentication.getPrincipal();
        CheckpointService.Created created = checkpointService.create(callerUserId, request);
        return CheckpointResponse.from(created.checkpoint(), created.qrCodeDataUri());
    }

    /** Scoped to the caller's own managed properties (ADMIN unrestricted) — needed to build a patrol route. */
    @GetMapping
    public List<CheckpointResponse> listByProperty(Authentication authentication, @RequestParam Long propertyId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return checkpointService.listByPropertyForCaller(callerUserId, propertyId).stream()
                .map(checkpoint -> CheckpointResponse.from(checkpoint, null))
                .toList();
    }

    @GetMapping("/{id}")
    public CheckpointResponse get(@PathVariable Long id) {
        CheckpointService.Created shareable = checkpointService.getShareable(id);
        return CheckpointResponse.from(shareable.checkpoint(), shareable.qrCodeDataUri());
    }
}
