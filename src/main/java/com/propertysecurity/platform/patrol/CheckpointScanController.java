package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.patrol.dto.CheckpointScanRequest;
import com.propertysecurity.platform.patrol.dto.CheckpointScanResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkpoint-scans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GUARD')")
public class CheckpointScanController {

    private final PatrolService patrolService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckpointScanResponse scan(Authentication authentication, @Valid @RequestBody CheckpointScanRequest request) {
        Long guardUserId = (Long) authentication.getPrincipal();
        return CheckpointScanResponse.from(patrolService.scan(guardUserId, request));
    }
}
