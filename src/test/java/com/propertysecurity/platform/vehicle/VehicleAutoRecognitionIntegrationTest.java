package com.propertysecurity.platform.vehicle;

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
 * Phase 3: a resident-registered vehicle gets auto-recognized at check-in,
 * an unrecognized one still gets captured, and vehicle history is scoped
 * to the querying guard's own property (per the dev-approved decision) —
 * ADMIN sees everything.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VehicleAutoRecognitionIntegrationTest {

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

    private record Fixtures(Long propertyId, Long residentUserId, Long guardUserId) {
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

        return new Fixtures(propertyId, residentUserId, guardUserId);
    }

    private String invitationBody(String visitorName) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime from = LocalDateTime.now().minusMinutes(1);
        LocalDateTime until = LocalDateTime.now().plusHours(2);
        return """
                {"visitorName":"%s","validFrom":"%s","validUntil":"%s"}
                """.formatted(visitorName, from.format(fmt), until.format(fmt));
    }

    private String createInvitation(String residentToken, String visitorName) throws Exception {
        String json = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationBody(visitorName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractString(json, "qrToken");
    }

    @Test
    void residentRegisteredVehicleIsRecognizedAtCheckIn() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Recognition Estate");
        String residentToken = tokenFor(fx.residentUserId(), Role.RESIDENT);
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"ca123gp\",\"make\":\"Toyota\",\"model\":\"Corolla\",\"colour\":\"White\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registration").value("CA123GP"));

        String qrToken = createInvitation(residentToken, "Vic Visitor");

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\",\"vehicleRegistration\":\"CA123GP\"}".formatted(qrToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleRegistration").value("CA123GP"))
                .andExpect(jsonPath("$.vehicleRecognized").value(true));
    }

    @Test
    void unrecognizedVehicleIsCapturedButNotFlagged() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Unrecognized Estate");
        String residentToken = tokenFor(fx.residentUserId(), Role.RESIDENT);
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        String qrToken = createInvitation(residentToken, "Vic Visitor");

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\",\"vehicleRegistration\":\"XY999ZZ\"}".formatted(qrToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleRegistration").value("XY999ZZ"))
                .andExpect(jsonPath("$.vehicleRecognized").value(false));
    }

    @Test
    void vehicleHistoryIsScopedToGuardsPropertyButUnscopedForAdmin() throws Exception {
        Fixtures propertyA = setUpPropertyWithResidentAndGuard("History Property A");
        Fixtures propertyB = setUpPropertyWithResidentAndGuard("History Property B");

        String residentAToken = tokenFor(propertyA.residentUserId(), Role.RESIDENT);
        String guardAToken = tokenFor(propertyA.guardUserId(), Role.GUARD);
        String guardBToken = tokenFor(propertyB.guardUserId(), Role.GUARD);

        String qrToken = createInvitation(residentAToken, "Shared Car Visitor");
        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\",\"vehicleRegistration\":\"SHARED1\"}".formatted(qrToken)))
                .andExpect(status().isCreated());

        // Guard at property A (where the visit happened) sees it.
        String guardAHistory = mockMvc.perform(get("/api/v1/visitor-entries/by-vehicle/SHARED1")
                        .header("Authorization", "Bearer " + guardAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(guardAHistory).contains("Shared Car Visitor");

        // Guard at property B (no visits there) sees nothing.
        String guardBHistory = mockMvc.perform(get("/api/v1/visitor-entries/by-vehicle/SHARED1")
                        .header("Authorization", "Bearer " + guardBToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(guardBHistory).isEqualTo("[]");

        // Admin is unscoped — sees it regardless of property.
        String adminHistory = mockMvc.perform(get("/api/v1/visitor-entries/by-vehicle/SHARED1")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(adminHistory).contains("Shared Car Visitor");
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
