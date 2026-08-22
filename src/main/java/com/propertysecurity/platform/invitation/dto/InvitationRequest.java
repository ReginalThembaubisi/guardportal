package com.propertysecurity.platform.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record InvitationRequest(
        @NotBlank @Size(max = 150) String visitorName,
        @Size(max = 20) String visitorPhone,
        @Size(max = 20) String expectedVehicleReg,
        @Size(max = 255) String purpose,
        @NotNull LocalDateTime validFrom,
        @NotNull LocalDateTime validUntil
) {
}
