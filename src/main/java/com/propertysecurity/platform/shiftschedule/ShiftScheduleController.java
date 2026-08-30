package com.propertysecurity.platform.shiftschedule;

import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleCreateRequest;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleImportRequest;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleImportResponse;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shift-schedules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public class ShiftScheduleController {

    private final ShiftScheduleService shiftScheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftScheduleResponse create(Authentication authentication, @Valid @RequestBody ShiftScheduleCreateRequest request) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return ShiftScheduleResponse.from(shiftScheduleService.create(callerUserId, request));
    }

    /** Bulk roster upload — see ShiftScheduleService.importSchedules. Never fails the whole batch over one bad row. */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftScheduleImportResponse bulkImport(Authentication authentication, @Valid @RequestBody ShiftScheduleImportRequest request) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return shiftScheduleService.importSchedules(callerUserId, request);
    }

    @GetMapping
    public List<ShiftScheduleResponse> listForProperty(Authentication authentication, @RequestParam Long propertyId) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return shiftScheduleService.listForProperty(callerUserId, propertyId).stream().map(ShiftScheduleResponse::from).toList();
    }

    /** The caller's own upcoming shifts — replaces checking a WhatsApp group. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('GUARD')")
    public List<ShiftScheduleResponse> mine(Authentication authentication) {
        Long guardUserId = (Long) authentication.getPrincipal();
        return shiftScheduleService.listUpcomingForGuard(guardUserId).stream().map(ShiftScheduleResponse::from).toList();
    }

    /** Backs the Clock In screen's context card. 204 when nothing is scheduled today. */
    @GetMapping("/today")
    @PreAuthorize("hasRole('GUARD')")
    public ResponseEntity<ShiftScheduleResponse> today(Authentication authentication) {
        Long guardUserId = (Long) authentication.getPrincipal();
        return shiftScheduleService.findTodayForGuardUser(guardUserId)
                .map(schedule -> ResponseEntity.ok(ShiftScheduleResponse.from(schedule)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        Long callerUserId = (Long) authentication.getPrincipal();
        shiftScheduleService.remove(callerUserId, id);
        return ResponseEntity.noContent().build();
    }
}
