package com.propertysecurity.platform.invitation;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.config.AppProperties;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.invitation.dto.InvitationRequest;
import com.propertysecurity.platform.resident.Resident;
import com.propertysecurity.platform.resident.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InvitationService {

    private static final int SHORT_CODE_MAX_ATTEMPTS = 20;
    private static final SecureRandom SHORT_CODE_RANDOM = new SecureRandom();

    private final InvitationRepository invitationRepository;
    private final ResidentRepository residentRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;

    public record Created(Invitation invitation, String checkInUrl, String qrCodeDataUri, String whatsappShareLink) {
    }

    public Created create(Long residentUserId, InvitationRequest request) {
        Resident resident = residentRepository.findByUser_IdAndDeletedAtIsNull(residentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No resident profile found for this account"));

        if (!request.validUntil().isAfter(request.validFrom())) {
            throw new BadRequestException("validUntil must be after validFrom");
        }
        if (!request.validUntil().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("validUntil must be in the future");
        }

        Invitation invitation = new Invitation();
        invitation.setResident(resident);
        invitation.setVisitorName(request.visitorName());
        invitation.setVisitorPhone(request.visitorPhone());
        invitation.setExpectedVehicleReg(request.expectedVehicleReg());
        invitation.setPurpose(request.purpose());
        invitation.setValidFrom(request.validFrom());
        invitation.setValidUntil(request.validUntil());
        invitation.setQrToken(UUID.randomUUID().toString());
        Long propertyId = resident.getUnit().getProperty().getId();
        invitation.setShortCode(generateShortCode(propertyId, request.validFrom(), request.validUntil()));
        invitation.setStatus(InvitationStatus.PENDING);

        Invitation saved = invitationRepository.save(invitation);

        auditLogService.record("invitation", saved.getId(), AuditAction.CREATE,
                residentUserId, null, snapshot(saved));

        String checkInUrl = buildCheckInUrl(saved.getQrToken());
        String qrCodeDataUri = qrCodeGenerator.generatePngDataUri(checkInUrl);
        String whatsappShareLink = buildWhatsAppLink(saved, checkInUrl);

        return new Created(saved, checkInUrl, qrCodeDataUri, whatsappShareLink);
    }

    @Transactional(readOnly = true)
    public List<Invitation> listForResident(Long residentUserId) {
        Resident resident = residentRepository.findByUser_IdAndDeletedAtIsNull(residentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No resident profile found for this account"));
        return invitationRepository.findAllByResident_IdOrderByCreatedAtDesc(resident.getId());
    }

    @Transactional(readOnly = true)
    public Invitation getForResident(Long residentUserId, Long invitationId) {
        Resident resident = residentRepository.findByUser_IdAndDeletedAtIsNull(residentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No resident profile found for this account"));
        return invitationRepository.findByIdAndResident_IdFetchResidentUnitAndProperty(invitationId, resident.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invitation " + invitationId + " not found"));
    }

    /** Recomputes the QR image + WhatsApp link for an existing invitation. Pure, not persisted/audited. */
    @Transactional(readOnly = true)
    public Created toShareable(Invitation invitation) {
        String checkInUrl = buildCheckInUrl(invitation.getQrToken());
        String qrCodeDataUri = qrCodeGenerator.generatePngDataUri(checkInUrl);
        String whatsappShareLink = buildWhatsAppLink(invitation, checkInUrl);
        return new Created(invitation, checkInUrl, qrCodeDataUri, whatsappShareLink);
    }

    public String buildCheckInUrl(String qrToken) {
        String base = appProperties.getCheckin().getBaseUrl();
        return (base.endsWith("/") ? base : base + "/") + qrToken;
    }

    private static final DateTimeFormatter WHATSAPP_DATE = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter WHATSAPP_TIME = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Code above the link: the link survives a copy-paste, the code is the
     * thing that needs to be read (often aloud, over a phone call at the
     * gate) — see the resident share screen's own note that these are two
     * doors into the same invitation.
     */
    private String buildWhatsAppLink(Invitation invitation, String checkInUrl) {
        String propertyName = invitation.getResident().getUnit().getProperty().getName();
        String message = "You're expected at " + propertyName + ",\n"
                + invitation.getValidFrom().format(WHATSAPP_DATE) + ", "
                + invitation.getValidFrom().format(WHATSAPP_TIME) + "–" + invitation.getValidUntil().format(WHATSAPP_TIME) + ".\n\n"
                + "Gate code: " + formatShortCode(invitation.getShortCode()) + "\n"
                + "Or show this at the gate:\n"
                + checkInUrl;
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

        String digitsOnlyPhone = Optional.ofNullable(invitation.getVisitorPhone())
                .map(phone -> phone.replaceAll("\\D", ""))
                .filter(digits -> !digits.isBlank())
                .orElse(null);

        return digitsOnlyPhone == null
                ? "https://wa.me/?text=" + encodedMessage
                : "https://wa.me/" + digitsOnlyPhone + "?text=" + encodedMessage;
    }

    /** 417302 -> "417 302" — the triple grouping is how the code is spoken. */
    private String formatShortCode(String shortCode) {
        return shortCode.length() > 3 ? shortCode.substring(0, 3) + " " + shortCode.substring(3) : shortCode;
    }

    /**
     * SecureRandom, not the entity id or a sequence — a guessable code would
     * make brute-forcing meaningfully easier. Collisions are only checked
     * against invitations that could actually be confused for this one
     * (same property, overlapping validity window, still PENDING); an old
     * invitation that's expired or used is free to share a digit
     * combination with a new one (see V13__add_invitation_short_code.sql).
     */
    private String generateShortCode(Long propertyId, LocalDateTime validFrom, LocalDateTime validUntil) {
        for (int attempt = 0; attempt < SHORT_CODE_MAX_ATTEMPTS; attempt++) {
            String candidate = String.format("%06d", SHORT_CODE_RANDOM.nextInt(1_000_000));
            if (!invitationRepository.existsOverlappingPendingShortCode(candidate, propertyId, validFrom, validUntil)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code after " + SHORT_CODE_MAX_ATTEMPTS + " attempts");
    }

    private Map<String, Object> snapshot(Invitation invitation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("residentId", invitation.getResident().getId());
        map.put("visitorName", invitation.getVisitorName());
        map.put("visitorPhone", invitation.getVisitorPhone());
        map.put("validFrom", invitation.getValidFrom());
        map.put("validUntil", invitation.getValidUntil());
        map.put("status", invitation.getStatus());
        return map;
    }
}
