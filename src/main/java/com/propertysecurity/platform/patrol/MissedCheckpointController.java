package com.propertysecurity.platform.patrol;

import com.propertysecurity.platform.patrol.dto.MissedCheckpointResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Kept separate from PatrolRouteController (PROPERTY_MANAGER/ADMIN only at
 * the class level) so a guard can check their own route's status too —
 * same reasoning as OccupancyController being split from the ADMIN-only
 * PropertyController.
 */
@RestController
@RequestMapping("/api/v1/patrol-routes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GUARD', 'PROPERTY_MANAGER', 'ADMIN')")
public class MissedCheckpointController {

    private final PatrolService patrolService;

    @GetMapping("/{id}/checkpoint-status")
    public MissedCheckpointResponse checkpointStatus(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return patrolService.missedCheckpoints(callerUserId, id, from, to);
    }
}
