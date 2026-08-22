package com.propertysecurity.platform.invitation.dto;

import com.propertysecurity.platform.invitation.Invitation;
import com.propertysecurity.platform.invitation.InvitationStatus;

import java.time.LocalDateTime;

public record InvitationResponse(
        Long id,
        String visitorName,
        String visitorPhone,
        String expectedVehicleReg,
        String purpose,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        InvitationStatus status,
        String qrToken,
        String checkInUrl,
        String qrCodeDataUri,
        String whatsappShareLink,
        LocalDateTime createdAt
) {
    public static InvitationResponse from(Invitation invitation, String checkInUrl, String qrCodeDataUri, String whatsappShareLink) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getVisitorName(),
                invitation.getVisitorPhone(),
                invitation.getExpectedVehicleReg(),
                invitation.getPurpose(),
                invitation.getValidFrom(),
                invitation.getValidUntil(),
                invitation.getStatus(),
                invitation.getQrToken(),
                checkInUrl,
                qrCodeDataUri,
                whatsappShareLink,
                invitation.getCreatedAt());
    }

    /** For list/get views where regenerating the QR image and WhatsApp link isn't needed. */
    public static InvitationResponse summaryOnly(Invitation invitation, String checkInUrl) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getVisitorName(),
                invitation.getVisitorPhone(),
                invitation.getExpectedVehicleReg(),
                invitation.getPurpose(),
                invitation.getValidFrom(),
                invitation.getValidUntil(),
                invitation.getStatus(),
                invitation.getQrToken(),
                checkInUrl,
                null,
                null,
                invitation.getCreatedAt());
    }
}
