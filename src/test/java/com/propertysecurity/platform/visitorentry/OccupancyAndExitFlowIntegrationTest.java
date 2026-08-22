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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 backend: live occupancy (grouped by category, excludes exited
 * visitors) and the exit flow, both scoped the same way as vehicle history
 * — guard to their own property, admin unscoped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OccupancyAndExitFlowIntegrationTest {

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

    private Long checkInVisitor(String residentToken, String guardToken) throws Exception {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime from = LocalDateTime.now().minusMinutes(1);
        LocalDateTime until = LocalDateTime.now().plusHours(2);
        String body = """
                {"visitorName":"Vic Visitor","validFrom":"%s","validUntil":"%s"}
                """.formatted(from.format(fmt), until.format(fmt));

        String invitationJson = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = extractString(invitationJson, "qrToken");

        String entryJson = mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\"}".formatted(qrToken)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extractLong(entryJson, "id");
    }

    @Test
    void occupancyShowsOnSiteVisitorsGroupedByCategoryAndExcludesExitedOnes() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Occupancy Estate");
        String residentToken = tokenFor(fx.residentUserId(), Role.RESIDENT);
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        Long entryId = checkInVisitor(residentToken, guardToken);

        mockMvc.perform(get("/api/v1/properties/" + fx.propertyId() + "/occupancy")
                        .header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOnSite").value(1))
                .andExpect(jsonPath("$.byCategory.VISITOR.length()").value(1))
                .andExpect(jsonPath("$.byCategory.CONTRACTOR.length()").value(0));

        mockMvc.perform(post("/api/v1/visitor-entries/" + entryId + "/exit")
                        .header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exitedAt").isNotEmpty())
                .andExpect(jsonPath("$.exitProcessedByGuardId").isNotEmpty());

        mockMvc.perform(get("/api/v1/properties/" + fx.propertyId() + "/occupancy")
                        .header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOnSite").value(0));
    }

    @Test
    void exitingAnAlreadyExitedVisitorIsRejected() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Double Exit Estate");
        String residentToken = tokenFor(fx.residentUserId(), Role.RESIDENT);
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        Long entryId = checkInVisitor(residentToken, guardToken);

        mockMvc.perform(post("/api/v1/visitor-entries/" + entryId + "/exit")
                        .header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/visitor-entries/" + entryId + "/exit")
                        .header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guardFromDifferentPropertyCannotViewOccupancyOrExitVisitors() throws Exception {
        Fixtures propertyA = setUpPropertyWithResidentAndGuard("Occupancy Property A");
        Fixtures propertyB = setUpPropertyWithResidentAndGuard("Occupancy Property B");

        String residentAToken = tokenFor(propertyA.residentUserId(), Role.RESIDENT);
        String guardAToken = tokenFor(propertyA.guardUserId(), Role.GUARD);
        String guardBToken = tokenFor(propertyB.guardUserId(), Role.GUARD);

        Long entryId = checkInVisitor(residentAToken, guardAToken);

        mockMvc.perform(get("/api/v1/properties/" + propertyA.propertyId() + "/occupancy")
                        .header("Authorization", "Bearer " + guardBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/visitor-entries/" + entryId + "/exit")
                        .header("Authorization", "Bearer " + guardBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewOccupancyForAnyProperty() throws Exception {
        Fixtures fx = setUpPropertyWithResidentAndGuard("Admin Occupancy Estate");
        String residentToken = tokenFor(fx.residentUserId(), Role.RESIDENT);
        String guardToken = tokenFor(fx.guardUserId(), Role.GUARD);

        checkInVisitor(residentToken, guardToken);

        mockMvc.perform(get("/api/v1/properties/" + fx.propertyId() + "/occupancy")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOnSite").value(1));
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
