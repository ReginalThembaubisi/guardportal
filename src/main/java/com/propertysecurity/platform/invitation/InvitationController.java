package com.propertysecurity.platform.invitation;

import com.propertysecurity.platform.invitation.dto.InvitationRequest;
import com.propertysecurity.platform.invitation.dto.InvitationResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RESIDENT')")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(Authentication authentication, @Valid @RequestBody InvitationRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        InvitationService.Created created = invitationService.create(userId, request);
        return InvitationResponse.from(created.invitation(), created.checkInUrl(), created.qrCodeDataUri(), created.whatsappShareLink());
    }

    @GetMapping
    public List<InvitationResponse> listOwn(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return invitationService.listForResident(userId).stream()
                .map(inv -> InvitationResponse.summaryOnly(inv, invitationService.buildCheckInUrl(inv.getQrToken())))
                .toList();
    }

    @GetMapping("/{id}")
    public InvitationResponse get(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        Invitation invitation = invitationService.getForResident(userId, id);
        // Regenerated on demand (deterministic from checkInUrl/visitor phone) so a resident
        // can come back and re-share the same invite without anything new being persisted.
        InvitationService.Created shareable = invitationService.toShareable(invitation);
        return InvitationResponse.from(shareable.invitation(), shareable.checkInUrl(), shareable.qrCodeDataUri(), shareable.whatsappShareLink());
    }
}
