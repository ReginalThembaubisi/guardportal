package com.propertysecurity.platform.invitation;

import com.propertysecurity.platform.security.JwtService;
import com.propertysecurity.platform.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Phase 2 flow: resident creates an invitation, a guard scans it
 * and gets a visitor_entry, and the audit chain (CLAUDE.md rule 2 + the
 * hash-chain from Phase 1) stays intact throughout. Per CLAUDE.md, audit
 * and access-control are the two areas that must be correctness-tested,
 * and this phase's core mechanic touches both.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InvitationCheckInFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String adminToken() {
        return jwtService.generateToken(1L, "admin@test", Set.of(Role.ADMIN));
    }

    private String tokenFor(Long userId, Role role) {
        return jwtService.generateToken(userId, "user" + userId + "@test", Set.of(role));
    }

    private record Fixtures(Long propertyId, Long unitId, Long residentUserId, Long guardUserId) {
    }

    private Fixtures setUpPropertyWithResidentAndGuard(String propertyName) throws Exception {
        String admin = adminToken();

        Long propertyId = extractLong(mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"address\":\"1 Test Rd\"}".formatted(propertyName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id");

        Long unitId = extractLong(mockMvc.perform(post("/api/v1/properties/" + propertyId + "/units")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitNumber\":\"1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id");

        Long residentUserId = extractLong(mockMvc.perform(post("/api/v1/residents")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unitId":%d,"fullName":"Res Ident","phoneNumber":"+2782%07d","email":"res%d@test.com"}
                                """.formatted(unitId, unitId, unitId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "userId");

        Long guardUserId = extractLong(mockMvc.perform(post("/api/v1/guards")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"fullName":"Guard Person","phoneNumber":"+2783%07d","email":"guard%d@test.com","password":"GuardPass123","badgeNumber":"B1"}
                                """.formatted(propertyId, propertyId, propertyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "userId");

        return new Fixtures(propertyId, unitId, residentUserId, guardUserId);
    }

    private String invitationBody() {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime from = LocalDateTime.now().minusMinutes(1);
        LocalDateTime until = LocalDateTime.now().plusHours(2);
        return """
                {"visitorName":"Vic Visitor","visitorPhone":"0821234567","purpose":"Delivery","validFrom":"%s","validUntil":"%s"}
                """.formatted(from.format(fmt), until.format(fmt));
    }

    @Test
    void residentCreatesInvitationAndGuardScansItSuccessfully() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Scan Success Estate");
        String residentToken = tokenFor(fx.residentUserId(), Role.RESIDENT);
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        String invitationJson = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String qrToken = extractString(invitationJson, "qrToken");
        String checkInUrl = extractString(invitationJson, "checkInUrl");
        String qrCodeDataUri = extractString(invitationJson, "qrCodeDataUri");
        String whatsappLink = extractString(invitationJson, "whatsappShareLink");

        assertThat(qrToken).isNotBlank();
        assertThat(checkInUrl).contains(qrToken);
        assertThat(qrCodeDataUri).startsWith("data:image/png;base64,");
        assertThat(whatsappLink).startsWith("https://wa.me/").contains("0821234567");

        // Guard at the same property scans it -> visitor_entry created.
        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\"}".formatted(qrToken)))
                .andExpect(status().isCreated());

        // Same token can't be used twice.
        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\"}".formatted(qrToken)))
                .andExpect(status().isBadRequest());

        // The hash chain still verifies after these real writes.
        mockMvc.perform(get("/api/v1/audit/verify").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void guardFromADifferentPropertyCannotScan() throws Exception {
        Fixtures inviteProperty = setUpPropertyWithResidentAndGuard("Property A");
        Fixtures otherProperty = setUpPropertyWithResidentAndGuard("Property B");

        String residentToken = tokenFor(inviteProperty.residentUserId(), Role.RESIDENT);
        String otherGuardToken = tokenFor(otherProperty.guardUserId(), Role.GUARD);

        String invitationJson = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = extractString(invitationJson, "qrToken");

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + otherGuardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\"}".formatted(qrToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonResidentCannotCreateInvitation() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Wrong Role Estate");
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationBody()))
                .andExpect(status().isForbidden());
    }

    private Long extractLong(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No field \"" + field + "\" in response: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String extractString(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No field \"" + field + "\" in response: " + json);
        }
        return matcher.group(1);
    }
}
