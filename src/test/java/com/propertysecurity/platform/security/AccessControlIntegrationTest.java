package com.propertysecurity.platform.security;

import com.propertysecurity.platform.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises Spring Security's role enforcement end to end (real JWTs, real
 * filter chain, real @PreAuthorize checks) rather than mocking it away.
 * Per CLAUDE.md, access control is one of the two areas that must be
 * correctness-tested, since it underpins the client's legal position.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccessControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String tokenFor(Role role) {
        return jwtService.generateToken(1L, "test-" + role + "@example.com", Set.of(role));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/properties"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateProperty() throws Exception {
        mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + tokenFor(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sunset Estate","address":"1 Main Rd"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void nonAdminCannotCreateProperty() throws Exception {
        mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + tokenFor(Role.GUARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sunset Estate","address":"1 Main Rd"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + tokenFor(Role.PROPERTY_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sunset Estate","address":"1 Main Rd"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void propertyManagerCanCreateResidentButGuardCannot() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        String propertyJson = mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sunset Estate","address":"1 Main Rd"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long propertyId = extractId(propertyJson);

        String unitJson = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/units")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unitNumber":"42"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long unitId = extractId(unitJson);

        String residentBody = """
                {"unitId":%d,"fullName":"Jane Resident","phoneNumber":"+27821234567","email":"jane@example.com"}
                """.formatted(unitId);

        mockMvc.perform(post("/api/v1/residents")
                        .header("Authorization", "Bearer " + tokenFor(Role.GUARD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(residentBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/residents")
                        .header("Authorization", "Bearer " + tokenFor(Role.PROPERTY_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(residentBody))
                .andExpect(status().isCreated());
    }

    @Test
    void authEndpointsArePublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private Long extractId(String json) {
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No id field in response: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }
}
