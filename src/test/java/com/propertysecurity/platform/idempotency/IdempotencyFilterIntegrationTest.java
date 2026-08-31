package com.propertysecurity.platform.idempotency;

import com.propertysecurity.platform.security.JwtService;
import com.propertysecurity.platform.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the idempotency filter failure paths — the contract
 * the frontend offline queue depends on before building against it.
 *
 * Covered scenarios:
 *   1. No header → request passes through unaffected
 *   2. Fresh key → 201 created; response is cached
 *   3. Replay (same key) → 201 with cached body (no duplicate DB row)
 *   4. In-flight (< 120 s) → 409
 *   5. Stale reclaim (≥ 120 s, simulated crash) → processes normally, not 409
 *   6. Principal mismatch → 403
 *   7. Endpoint mismatch → 422
 *
 * NOTE: Not @Transactional — the filter's REQUIRES_NEW sub-transactions conflict
 * with H2's table-level locking when the test holds an outer transaction on the
 * same table. Each test creates its own guards/keys (fresh UUIDs each time),
 * so there is no cross-test data collision even without rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdempotencyFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private Long propertyId;
    private Long guardUserId;
    private Long guard2UserId;
    private String guardToken;
    private String guard2Token;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = jwtService.generateToken(1L, "admin@test", Set.of(Role.ADMIN));

        // Property
        String propJson = mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Idem Test Estate\",\"address\":\"1 Idem Rd\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        propertyId = extractLong(propJson, "id");

        // Guard 1
        String g1 = mockMvc.perform(post("/api/v1/guards")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"fullName":"Guard One","phoneNumber":"+2783%07d","email":"g1_%d@test.com","password":"GuardPass1!","badgeNumber":"I1"}
                                """.formatted(propertyId, propertyId, propertyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        guardUserId = extractLong(g1, "userId");
        guardToken = jwtService.generateToken(guardUserId, "g1_" + propertyId + "@test.com", Set.of(Role.GUARD));

        // Guard 2 (for principal-mismatch test)
        String g2 = mockMvc.perform(post("/api/v1/guards")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"fullName":"Guard Two","phoneNumber":"+2784%07d","email":"g2_%d@test.com","password":"GuardPass1!","badgeNumber":"I2"}
                                """.formatted(propertyId, propertyId, propertyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        guard2UserId = extractLong(g2, "userId");
        guard2Token = jwtService.generateToken(guard2UserId, "g2_" + propertyId + "@test.com", Set.of(Role.GUARD));
    }

    @Test
    void noHeader_passesThrough() throws Exception {
        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isCreated());
    }

    @Test
    void freshKey_createsAndCaches() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + guardToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        IdempotencyKey row = idempotencyKeyRepository.findByIdemKey(key).orElseThrow();
        assertThat(row.isInFlight()).isFalse();
        assertThat(row.getStatusCode()).isEqualTo(201);
        assertThat(row.getResponseBody()).contains("\"id\"");
    }

    @Test
    void replayKey_returnsCachedResponseWithoutDuplicate() throws Exception {
        String key = UUID.randomUUID().toString();

        String firstBody = mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + guardToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long firstShiftId = extractLong(firstBody, "id");

        // Second request with the same key — must replay, not create a second shift.
        String replayBody = mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + guardToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long replayShiftId = extractLong(replayBody, "id");
        assertThat(replayShiftId).isEqualTo(firstShiftId);
    }

    @Test
    void inFlightKey_returns409() throws Exception {
        String key = UUID.randomUUID().toString();

        // Insert a fresh in-flight row directly (simulates another request mid-processing).
        jdbcTemplate.update(
                "INSERT INTO idempotency_key (idem_key, endpoint, principal_id, in_flight, created_at) VALUES (?, 'POST /api/v1/shifts', ?, 1, NOW())",
                key, guardUserId);

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + guardToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isConflict());

        // The in_flight row must still be intact (we didn't own it).
        IdempotencyKey row = idempotencyKeyRepository.findByIdemKey(key).orElseThrow();
        assertThat(row.isInFlight()).isTrue();
    }

    /**
     * Simulates the "kill process mid-request" scenario:
     * a row is inserted as in_flight but its created_at is more than 120 s ago
     * (the JVM crashed before the finally block could finalize it). The next
     * request must reclaim it, process the request, and return a success — not
     * 409 forever.
     */
    @Test
    void staleInFlight_reclaimsAndCompletes() throws Exception {
        String key = UUID.randomUUID().toString();

        // DATEADD('SECOND', -121, NOW()) is H2-compatible syntax for "121 seconds ago".
        jdbcTemplate.update(
                "INSERT INTO idempotency_key (idem_key, endpoint, principal_id, in_flight, created_at) VALUES (?, 'POST /api/v1/shifts', ?, 1, DATEADD('SECOND', -121, NOW()))",
                key, guardUserId);

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + guardToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        IdempotencyKey row = idempotencyKeyRepository.findByIdemKey(key).orElseThrow();
        assertThat(row.isInFlight()).isFalse();
        assertThat(row.getStatusCode()).isEqualTo(201);
    }

    @Test
    void principalMismatch_returns403() throws Exception {
        String key = UUID.randomUUID().toString();

        // Guard 1 claims the key.
        jdbcTemplate.update("""
                INSERT INTO idempotency_key (idem_key, endpoint, principal_id, in_flight, status_code, response_body, created_at)
                VALUES (?, 'POST /api/v1/shifts', ?, 0, 201, '{"id":999}', NOW())
                """, key, guardUserId);

        // Guard 2 tries to replay it.
        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", "Bearer " + guard2Token)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointMismatch_returns422() throws Exception {
        String key = UUID.randomUUID().toString();

        // Key was completed for clock-in.
        jdbcTemplate.update("""
                INSERT INTO idempotency_key (idem_key, endpoint, principal_id, in_flight, status_code, response_body, created_at)
                VALUES (?, 'POST /api/v1/shifts', ?, 0, 201, '{"id":999}', NOW())
                """, key, guardUserId);

        // Same guard tries to use the same key for clock-out.
        mockMvc.perform(post("/api/v1/shifts/clock-out")
                        .header("Authorization", "Bearer " + guardToken)
                        .header(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("POST /api/v1/shifts")));
    }

    private Long extractLong(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("No field \"" + field + "\" in: " + json);
        }
        return Long.parseLong(m.group(1));
    }
}
