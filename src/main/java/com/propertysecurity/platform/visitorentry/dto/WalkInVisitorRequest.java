package com.propertysecurity.platform.visitorentry.dto;

import com.propertysecurity.platform.visitorentry.VisitorCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** No invitation code — a guard capturing a walk-in visitor's details directly. */
public record WalkInVisitorRequest(
        @NotBlank @Size(max = 150) String visitorName,
        @Size(max = 20) String visitorPhone,
        @NotNull VisitorCategory category,
        @Size(max = 500) String purpose,
        /** Destination unit, if the visitor names one — optional, same as visitor_entry.unit_id itself. */
        Long unitId
) {
}
