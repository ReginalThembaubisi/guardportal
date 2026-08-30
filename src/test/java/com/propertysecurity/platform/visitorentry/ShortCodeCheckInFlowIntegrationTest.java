package com.propertysecurity.platform.visitorentry;

import com.propertysecurity.platform.security.JwtService;
import com.propertysecurity.platform.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The short-code check-in path added alongside qrToken: a guard types the
 * 6-digit code instead of scanning. Covers the four classifiable outcomes
 * (success, not-found, already-used, both-fields-provided) — the two this
 * test doesn't exercise (expired, not-yet-valid) share the exact same
 * classifyRejection() branch shape as already-used, just on the time
 * window instead of status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShortCodeCheckInFlowIntegrationTest {

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
    void guardChecksInWithShortCode() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Short Code Success Estate");
        String residentToken = tokenFor(fx.residentUserId(), Role.RESIDENT);
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        String invitationJson = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String shortCode = extractString(invitationJson, "shortCode");
        org.assertj.core.api.Assertions.assertThat(shortCode).hasSize(6).matches("\\d{6}");

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortCode\":\"%s\"}".formatted(shortCode)))
                .andExpect(status().isCreated());

        // Now USED — the same code is rejected as already-used, not silently re-matched.
        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortCode\":\"%s\"}".formatted(shortCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("ALREADY_USED"));
    }

    @Test
    void unknownShortCodeIsClassifiedNotFound() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Short Code Not Found Estate");
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shortCode\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("NOT_FOUND"));
    }

    @Test
    void providingBothQrTokenAndShortCodeIsRejected() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Both Fields Estate");
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"11111111-1111-1111-1111-111111111111\",\"shortCode\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providingNeitherQrTokenNorShortCodeIsRejected() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Neither Field Estate");
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
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
