package com.propertysecurity.platform.propertymanager;

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
 * Closes the SUPERVISOR/PROPERTY_MANAGER scoping gap flagged since Phase 1:
 * a property manager linked to a property can view its occupancy; one not
 * linked to it cannot; a resident sees their own visitor history via
 * invitations they created.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PropertyManagerScopingIntegrationTest {

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

    @Test
    void propertyManagerCanOnlyViewOccupancyForPropertiesTheyManage() throws Exception {
        String admin = adminToken();

        Long propertyAId = extractLong(mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"PM Property A\",\"address\":\"1 Test Rd\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id");

        Long propertyBId = extractLong(mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"PM Property B\",\"address\":\"2 Test Rd\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id");

        // Create a PROPERTY_MANAGER staff account, then link it to property A only.
        Long pmUserId = extractLong(mockMvc.perform(post("/api/v1/staff")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"PM Person","phoneNumber":"+27821110000","email":"pm@test.com","password":"PmPass123","role":"PROPERTY_MANAGER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "id");

        mockMvc.perform(post("/api/v1/property-managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":%d,\"propertyId\":%d}".formatted(pmUserId, propertyAId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propertyId").value(propertyAId));

        String pmToken = tokenFor(pmUserId, Role.PROPERTY_MANAGER);

        // "/mine" reflects exactly the one linked property.
        mockMvc.perform(get("/api/v1/property-managers/mine").header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].propertyId").value(propertyAId));

        mockMvc.perform(get("/api/v1/properties/" + propertyAId + "/occupancy").header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/properties/" + propertyBId + "/occupancy").header("Authorization", "Bearer " + pmToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void residentSeesOnlyTheirOwnVisitorHistory() throws Exception {
        String admin = adminToken();

        Long propertyId = extractLong(mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"History Estate\",\"address\":\"3 Test Rd\"}"))
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
                        .content("{\"unitId\":%d,\"fullName\":\"Res Ident\",\"phoneNumber\":\"+27822220000\",\"email\":\"res2@test.com\"}".formatted(unitId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "userId");

        Long guardUserId = extractLong(mockMvc.perform(post("/api/v1/guards")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":%d,\"fullName\":\"Guard Person\",\"phoneNumber\":\"+27823330000\",\"email\":\"guard2@test.com\",\"password\":\"GuardPass123\",\"badgeNumber\":\"B1\"}".formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "userId");

        String residentToken = tokenFor(residentUserId, Role.RESIDENT);
        String guardToken = tokenFor(guardUserId, Role.GUARD);

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime from = LocalDateTime.now().minusMinutes(1);
        LocalDateTime until = LocalDateTime.now().plusHours(2);
        String invitationJson = mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitorName\":\"My Visitor\",\"validFrom\":\"%s\",\"validUntil\":\"%s\"}"
                                .formatted(from.format(fmt), until.format(fmt))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrToken = extractString(invitationJson, "qrToken");

        mockMvc.perform(post("/api/v1/visitor-entries")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"%s\"}".formatted(qrToken)))
                .andExpect(status().isCreated());

        String myHistory = mockMvc.perform(get("/api/v1/visitor-entries/mine").header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(myHistory).contains("My Visitor");

        // A guard token can't see the resident-only "mine" view.
        mockMvc.perform(get("/api/v1/visitor-entries/mine").header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isForbidden());
    }
}
