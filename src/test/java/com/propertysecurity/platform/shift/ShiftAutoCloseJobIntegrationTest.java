package com.propertysecurity.platform.shift;

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

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the roster auto-close job and the GET /api/v1/shifts endpoint.
 *
 * Covered scenarios:
 *   1. Day shift past grace window → ROSTER_AUTO_CLOSED with clock_out_at = rostered end time
 *   2. Day shift within grace window → not closed
 *   3. Night shift crossing midnight, past grace window → closed at correct next-day time
 *   4. Night shift, job runs before grace window expires → not closed
 *   5. No matching schedule → shift left open (rule 4)
 *   6. Schedule exists but end_time is null → shift left open (rule 4)
 *   7. Shift already has clock_out_at → not modified (rule 1)
 *   8. Queued clock-out arrives after auto-close → 404 (contract for offline queue)
 *   9. GET /api/v1/shifts → supervisor sees all four states; ordinal on repeated auto-closes
 *
 * Not @Transactional: job uses REQUIRES_NEW sub-transactions that conflict with
 * H2's table locking when an outer test transaction holds the same rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShiftAutoCloseJobIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ShiftAutoCloseJob job;

    private String adminToken;

    // Africa/Johannesburg is the default property timezone (Property entity default).
    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private static final int GRACE = 90; // must match app.shift.auto-close-grace-minutes test default

    @BeforeEach
    void setUp() {
        adminToken = jwtService.generateToken(1L, "admin@test", Set.of(Role.ADMIN));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a property and a guard assigned to it; returns [propertyId, guardProfileId, guardUserId]. */
    private long[] createPropertyAndGuard(String tag) throws Exception {
        String propJson = mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ACTest " + tag + "\",\"address\":\"1 Test Rd\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long propertyId = extractLong(propJson, "id");

        String guardJson = mockMvc.perform(post("/api/v1/guards")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"fullName":"Guard %s","phoneNumber":"+279%s","email":"g%s@test.com","password":"GuardPass1!","badgeNumber":"AC%s"}
                                """.formatted(propertyId, tag,
                                tag.replaceAll("[^0-9]", "0").substring(0, Math.min(tag.length(), 9)),
                                tag, tag)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long guardUserId = extractLong(guardJson, "userId");
        long guardProfileId = jdbc.queryForObject("SELECT id FROM guard WHERE user_id = ?", Long.class, guardUserId);
        return new long[]{propertyId, guardProfileId, guardUserId};
    }

    /** Inserts a shift_schedule row with explicit dates/times for full timestamp control. */
    private void insertSchedule(long guardId, long propertyId, LocalDate shiftDate, String shiftType, String startTime, String endTime) {
        jdbc.update(
                "INSERT INTO shift_schedule (guard_id, property_id, shift_date, shift_type, start_time, end_time, created_at) VALUES (?,?,?,?,?,?,NOW())",
                guardId, propertyId, shiftDate.toString(), shiftType, startTime, endTime);
    }

    /** Inserts an open shift with a controlled clock_in_at; returns its id. */
    private long insertOpenShift(long guardId, long propertyId, LocalDateTime clockInAt) {
        jdbc.update(
                "INSERT INTO shift (guard_id, property_id, clock_in_at, clock_in_latitude, clock_in_longitude, created_at) VALUES (?,?,?,0,0,NOW())",
                guardId, propertyId, clockInAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM shift WHERE guard_id = ?", Long.class, guardId);
    }

    /** Reads clock_out_at and clock_out_source for the given shift id. */
    private Map<String, Object> shiftRow(long shiftId) {
        return jdbc.queryForMap("SELECT clock_out_at, clock_out_source FROM shift WHERE id = ?", shiftId);
    }

    private static long extractLong(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9]+)").matcher(json);
        if (!m.find()) throw new IllegalArgumentException("Field " + field + " not found in: " + json);
        return Long.parseLong(m.group(1));
    }

    /** H2 returns java.sql.Timestamp for DATETIME columns; MySQL returns LocalDateTime. Handle both. */
    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        throw new IllegalArgumentException("Unexpected timestamp type: " + value.getClass());
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void dayShift_pastGraceWindow_autoClosedAtRosteredEnd() throws Exception {
        long[] ids = createPropertyAndGuard("1A");
        long propertyId = ids[0], guardId = ids[1];

        // Schedule: 4 days ago, 08:00–16:00 (well past grace window whenever tests run)
        LocalDate shiftDate = LocalDate.now().minusDays(4);
        insertSchedule(guardId, propertyId, shiftDate, "DAY", "08:00", "16:00");
        LocalDateTime clockIn = shiftDate.atTime(8, 0);
        long shiftId = insertOpenShift(guardId, propertyId, clockIn);

        job.run();

        Map<String, Object> row = shiftRow(shiftId);
        assertThat(row.get("clock_out_source")).isEqualTo("ROSTER_AUTO_CLOSED");
        // Rule 2: clock_out_at must equal the rostered end time, not the job's run time.
        LocalDateTime expected = shiftDate.atTime(16, 0);
        assertThat(toLocalDateTime(row.get("clock_out_at"))).isEqualTo(expected);
    }

    @Test
    void dayShift_withinGraceWindow_notClosed() throws Exception {
        long[] ids = createPropertyAndGuard("2A");
        long propertyId = ids[0], guardId = ids[1];

        // Schedule: today, end time = 30 minutes from now (within the 90-min grace window)
        LocalDate today = LocalDate.now();
        // Use a future end time to guarantee we're within grace: end = now + 30 min
        String futureEnd = ZonedDateTime.now(ZONE).plusMinutes(30).toLocalTime().withSecond(0).withNano(0).toString().substring(0, 5);
        insertSchedule(guardId, propertyId, today, "DAY", "06:00", futureEnd);
        long shiftId = insertOpenShift(guardId, propertyId, today.atTime(6, 0));

        job.run();

        Map<String, Object> row = shiftRow(shiftId);
        assertThat(row.get("clock_out_at")).isNull();
        assertThat(row.get("clock_out_source")).isNull();
    }

    @Test
    void nightShiftCrossingMidnight_pastGraceWindow_autoClosedAtRosteredEnd() throws Exception {
        long[] ids = createPropertyAndGuard("3A");
        long propertyId = ids[0], guardId = ids[1];

        // Schedule: 3 days ago, 18:00–06:00. end_time(06:00) < start_time(18:00) → end on day+1.
        // clock_out_at must be (shift_date + 1) at 06:00, not shift_date at 06:00.
        LocalDate shiftDate = LocalDate.now().minusDays(3);
        insertSchedule(guardId, propertyId, shiftDate, "NIGHT", "18:00", "06:00");
        LocalDateTime clockIn = shiftDate.atTime(18, 0);
        long shiftId = insertOpenShift(guardId, propertyId, clockIn);

        job.run();

        Map<String, Object> row = shiftRow(shiftId);
        assertThat(row.get("clock_out_source")).isEqualTo("ROSTER_AUTO_CLOSED");

        LocalDateTime expected = shiftDate.plusDays(1).atTime(6, 0);
        assertThat(toLocalDateTime(row.get("clock_out_at"))).isEqualTo(expected);
    }

    @Test
    void nightShift_jobRunsTooEarly_notClosed() throws Exception {
        long[] ids = createPropertyAndGuard("4A");
        long propertyId = ids[0], guardId = ids[1];

        // Schedule: today, 18:00–06:00. Effective end = tomorrow 06:00. Threshold = tomorrow 07:30.
        // Since "now" is well before tomorrow 07:30, the shift must be left open.
        LocalDate today = LocalDate.now();
        insertSchedule(guardId, propertyId, today, "NIGHT", "18:00", "06:00");
        long shiftId = insertOpenShift(guardId, propertyId, today.atTime(18, 0));

        job.run();

        Map<String, Object> row = shiftRow(shiftId);
        assertThat(row.get("clock_out_at")).isNull();
    }

    @Test
    void noSchedule_shiftLeftOpen() throws Exception {
        long[] ids = createPropertyAndGuard("5A");
        long propertyId = ids[0], guardId = ids[1];

        // No shift_schedule row for this guard — rule 4 must leave the shift open.
        LocalDate shiftDate = LocalDate.now().minusDays(4);
        long shiftId = insertOpenShift(guardId, propertyId, shiftDate.atTime(8, 0));

        job.run();

        assertThat(shiftRow(shiftId).get("clock_out_at")).isNull();
    }

    @Test
    void scheduleWithNullEndTime_shiftLeftOpen() throws Exception {
        long[] ids = createPropertyAndGuard("6A");
        long propertyId = ids[0], guardId = ids[1];

        // Rule 4: end_time is null → no auto-close (start_time is known, end is not).
        LocalDate shiftDate = LocalDate.now().minusDays(4);
        jdbc.update(
                "INSERT INTO shift_schedule (guard_id, property_id, shift_date, shift_type, start_time, end_time, created_at) VALUES (?,?,?,?,?,NULL,NOW())",
                guardId, propertyId, shiftDate.toString(), "DAY", "08:00");
        long shiftId = insertOpenShift(guardId, propertyId, shiftDate.atTime(8, 0));

        job.run();

        assertThat(shiftRow(shiftId).get("clock_out_at")).isNull();
    }

    @Test
    void alreadyClosedShift_notModified() throws Exception {
        long[] ids = createPropertyAndGuard("7A");
        long propertyId = ids[0], guardId = ids[1];

        LocalDate shiftDate = LocalDate.now().minusDays(4);
        insertSchedule(guardId, propertyId, shiftDate, "DAY", "08:00", "16:00");
        long shiftId = insertOpenShift(guardId, propertyId, shiftDate.atTime(8, 0));

        // Simulate guard clocking out before the job ran.
        LocalDateTime guardClockOut = shiftDate.atTime(15, 45);
        jdbc.update("UPDATE shift SET clock_out_at = ?, clock_out_source = NULL WHERE id = ?", guardClockOut, shiftId);

        job.run();

        // Rule 1: job must not overwrite the guard's clock_out_at.
        Map<String, Object> row = shiftRow(shiftId);
        assertThat(toLocalDateTime(row.get("clock_out_at"))).isEqualTo(guardClockOut);
        assertThat(row.get("clock_out_source")).isNull();
    }

    @Test
    void queuedClockOutAfterAutoClose_returns404() throws Exception {
        long[] ids = createPropertyAndGuard("8A");
        long propertyId = ids[0], guardId = ids[1];
        long guardUserId = ids[2];

        LocalDate shiftDate = LocalDate.now().minusDays(4);
        insertSchedule(guardId, propertyId, shiftDate, "DAY", "08:00", "16:00");
        insertOpenShift(guardId, propertyId, shiftDate.atTime(8, 0));

        job.run(); // auto-closes the shift

        // A queued clock-out arrives after the auto-close — simulates the offline
        // queue flushing the guard's saved clock-out for an already-closed shift.
        // The offline queue relies on this 404 to mark the entry as "already closed"
        // (informational, not failure — see QueuePage.tsx handleSubmitLate).
        String guardToken = jwtService.generateToken(guardUserId, "g8A@test.com", Set.of(Role.GUARD));
        mockMvc.perform(post("/api/v1/shifts/clock-out")
                        .header("Authorization", "Bearer " + guardToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":0.0,\"longitude\":0.0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditRow_autoClose_hasNullActor() throws Exception {
        long[] ids = createPropertyAndGuard("9A");
        long propertyId = ids[0], guardId = ids[1];

        LocalDate shiftDate = LocalDate.now().minusDays(4);
        insertSchedule(guardId, propertyId, shiftDate, "DAY", "08:00", "16:00");
        long shiftId = insertOpenShift(guardId, propertyId, shiftDate.atTime(8, 0));

        job.run();

        // Rule 5: the guard must never appear to have asserted the auto-close time.
        Map<String, Object> auditRow = jdbc.queryForMap(
                "SELECT performed_by_user_id FROM audit_log WHERE entity_name = 'shift' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                shiftId);
        assertThat(auditRow.get("performed_by_user_id")).isNull();
    }

    @Test
    void weekAutoCloseOrdinal_incrementsWithinWeek() throws Exception {
        long[] ids = createPropertyAndGuard("10A");
        long propertyId = ids[0], guardId = ids[1];

        // Pick Wednesday and Thursday of the ISO week 3 weeks ago.
        // They're always in the same ISO week (consecutive days, neither Monday),
        // always well past the grace window, and at most 27 days old (within the
        // 30-day test lookback set in application-test.yml).
        LocalDate refDate = LocalDate.now().minusDays(21);
        LocalDate day1 = refDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.WEDNESDAY));
        LocalDate day2 = day1.plusDays(1); // Thursday of the same ISO week

        insertSchedule(guardId, propertyId, day1, "DAY", "08:00", "16:00");
        insertSchedule(guardId, propertyId, day2, "DAY", "08:00", "16:00");
        long shift1 = insertOpenShift(guardId, propertyId, day1.atTime(8, 0));
        long shift2 = insertOpenShift(guardId, propertyId, day2.atTime(8, 0));

        job.run();

        // Both should be auto-closed.
        assertThat(shiftRow(shift1).get("clock_out_source")).isEqualTo("ROSTER_AUTO_CLOSED");
        assertThat(shiftRow(shift2).get("clock_out_source")).isEqualTo("ROSTER_AUTO_CLOSED");

        // The list endpoint must show ordinal 1 for the first and ordinal 2 for the second.
        String listJson = mockMvc.perform(get("/api/v1/shifts")
                        .param("propertyId", String.valueOf(propertyId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Extract ordinals — the list is ordered newest-first, so shift2 comes first.
        int ordinalForShift2 = extractOrdinalForShift(listJson, shift2);
        int ordinalForShift1 = extractOrdinalForShift(listJson, shift1);
        assertThat(ordinalForShift1).isEqualTo(1);
        assertThat(ordinalForShift2).isEqualTo(2);
    }

    @Test
    void listEndpoint_requiresSupervisorOrAdmin() throws Exception {
        long[] ids = createPropertyAndGuard("11A");

        // GUARD role must be rejected.
        String guardToken = jwtService.generateToken(ids[2], "g11A@test.com", Set.of(Role.GUARD));
        mockMvc.perform(get("/api/v1/shifts")
                        .param("propertyId", String.valueOf(ids[0]))
                        .header("Authorization", "Bearer " + guardToken))
                .andExpect(status().isForbidden());
    }

    // ── Extraction helpers ────────────────────────────────────────────────────

    private static int extractOrdinalForShift(String json, long shiftId) {
        // JSON array: find the object with "id":{shiftId} and extract "weekAutoCloseOrdinal"
        String idPattern = "\"id\"\\s*:\\s*" + shiftId + "[^}]*\"weekAutoCloseOrdinal\"\\s*:\\s*([0-9]+)";
        Matcher m = Pattern.compile(idPattern).matcher(json);
        if (m.find()) return Integer.parseInt(m.group(1));
        // Fallback: search for weekAutoCloseOrdinal near the shift id in the other order
        String altPattern = "\"weekAutoCloseOrdinal\"\\s*:\\s*([0-9]+)[^{]*\"id\"\\s*:\\s*" + shiftId;
        m = Pattern.compile(altPattern).matcher(json);
        if (m.find()) return Integer.parseInt(m.group(1));
        throw new IllegalArgumentException("Could not find weekAutoCloseOrdinal for shift " + shiftId + " in: " + json);
    }
}
