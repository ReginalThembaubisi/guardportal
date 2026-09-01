package com.propertysecurity.platform.shift;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.common.GeoDistance;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.propertysupervisor.PropertySupervisorRepository;
import com.propertysecurity.platform.shift.dto.LocationRequest;
import com.propertysecurity.platform.shift.dto.ShiftSummaryResponse;
import com.propertysecurity.platform.shiftschedule.ShiftSchedule;
import com.propertysecurity.platform.shiftschedule.ShiftScheduleRepository;
import com.propertysecurity.platform.shiftschedule.ShiftScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * First pass on GPS verification, not a hardened anti-fraud system —
 * guards use their own phones (no dedicated property devices), so GPS
 * accuracy varies. An out-of-tolerance clock-in/out is flagged for later
 * review, never blocked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final GuardRepository guardRepository;
    private final AuditLogService auditLogService;
    private final ShiftScheduleService shiftScheduleService;
    private final ShiftScheduleRepository shiftScheduleRepository;
    private final PropertySupervisorRepository propertySupervisorRepository;

    @Value("${app.geo.default-tolerance-meters:150}")
    private int defaultToleranceMeters;

    /** A claimed clock-out time more than this far behind server-now is flagged CLIENT_CLAIMED_LATE.
     *  Normal offline queue flush arrives within a shift window (typically < 12h);
     *  manual late submissions from expired queue entries arrive 72h+ after the claimed time. */
    private static final Duration CLOCK_OUT_STALENESS_THRESHOLD = Duration.ofHours(4);

    public Shift clockIn(Long guardUserId, LocationRequest location) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        if (shiftRepository.findByGuard_IdAndClockOutAtIsNull(guard.getId()).isPresent()) {
            throw new BadRequestException("You already have an open shift — clock out first");
        }

        Property property = guard.getProperty();
        GeoCheck check = checkLocation(property, location);

        // All shift timestamps are stored as property-local wall-clock time so that
        // shiftDate comparisons, night-shift midnight-crossing, and auto-close are
        // all done in the same timezone as the roster.  LocalDateTime.now() uses the
        // JVM zone, which may differ from the property zone on a cloud host.
        ZoneId propertyZone = ZoneId.of(property.getTimezone());
        LocalDateTime clockInAt = LocalDateTime.now(propertyZone);

        Shift shift = new Shift();
        shift.setGuard(guard);
        shift.setProperty(property);
        shift.setClockInAt(clockInAt);
        shift.setClientClaimedClockInAt(validatedClaim(location.clientClaimedAt(), null, clockInAt, guardUserId, "clock-in"));
        shift.setClockInLatitude(location.latitude());
        shift.setClockInLongitude(location.longitude());
        shift.setClockInDistanceMeters(check.distanceMeters());
        shift.setClockInWithinTolerance(check.withinTolerance());
        shift.setShiftType(resolveShiftType(guard.getId(), clockInAt));

        Shift saved = shiftRepository.save(shift);
        auditLogService.record("shift", saved.getId(), AuditAction.CREATE, guardUserId, null, snapshot(saved));
        return saved;
    }

    /** Read-only — lets a guard's app recover its clocked-in/out state on load instead of relying on local caching. */
    @Transactional(readOnly = true)
    public Optional<Shift> getCurrentShift(Long guardUserId) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));
        return shiftRepository.findOpenByGuardIdFetchProperty(guard.getId());
    }

    public Shift clockOut(Long guardUserId, LocationRequest location) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));

        Shift shift = shiftRepository.findOpenByGuardIdFetchProperty(guard.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No open shift found for this guard"));

        GeoCheck check = checkLocation(shift.getProperty(), location);

        LocalDateTime clockOutAt = LocalDateTime.now(ZoneId.of(shift.getProperty().getTimezone()));
        LocalDateTime claimedClockOutAt = validatedClaim(location.clientClaimedAt(), shift.getClockInAt(), clockOutAt, guardUserId, "clock-out");
        shift.setClockOutAt(clockOutAt);
        shift.setClientClaimedClockOutAt(claimedClockOutAt);
        shift.setClockOutLatitude(location.latitude());
        shift.setClockOutLongitude(location.longitude());
        shift.setClockOutDistanceMeters(check.distanceMeters());
        shift.setClockOutWithinTolerance(check.withinTolerance());

        ClockOutSource source = null;
        if (claimedClockOutAt != null && Duration.between(claimedClockOutAt, clockOutAt).compareTo(CLOCK_OUT_STALENESS_THRESHOLD) > 0) {
            source = ClockOutSource.CLIENT_CLAIMED_LATE;
        }
        shift.setClockOutSource(source);

        Shift saved = shiftRepository.save(shift);
        auditLogService.record("shift", saved.getId(), AuditAction.UPDATE, guardUserId,
                Map.of("clockOutAt", "null"),
                Map.of("clockOutAt", saved.getClockOutAt(), "clockOutWithinTolerance",
                        String.valueOf(saved.getClockOutWithinTolerance())));
        return saved;
    }

    /**
     * Uses today's scheduled shift's DAY/NIGHT when one exists (the
     * Supervisor-uploaded roster is the source of truth); otherwise derives
     * it from the clock-in hour so the field is never left blank.
     */
    private ShiftType resolveShiftType(Long guardId, LocalDateTime clockInAt) {
        Optional<ShiftSchedule> scheduled = shiftScheduleService.findTodayForGuard(guardId);
        if (scheduled.isPresent()) {
            return scheduled.get().getShiftType();
        }
        int hour = clockInAt.getHour();
        return (hour >= 6 && hour < 18) ? ShiftType.DAY : ShiftType.NIGHT;
    }

    /**
     * Stores the claim as-is but logs a warning when the phone clock looks
     * wrong. Principle #3: flag, don't block. A phone with a skewed clock is
     * an anomaly worth recording; rejecting the claim would destroy the only
     * record of the guard's intent. lowerBound is the server clock-in time
     * (null for clock-in itself where there is no prior bound).
     */
    private LocalDateTime validatedClaim(LocalDateTime claimed, LocalDateTime lowerBound,
                                          LocalDateTime serverNow, Long guardUserId, String event) {
        if (claimed == null) {
            return null;
        }
        boolean tooEarly = lowerBound != null && claimed.isBefore(lowerBound);
        boolean tooLate = claimed.isAfter(serverNow);
        if (tooEarly || tooLate) {
            log.warn("Guard {} submitted a {} claim ({}) outside the valid window [{}, {}]",
                    guardUserId, event, claimed, lowerBound, serverNow);
        }
        return claimed;
    }

    private record GeoCheck(Integer distanceMeters, Boolean withinTolerance) {
    }

    /** Null distance/withinTolerance when the property has no known location yet to compare against. */
    private GeoCheck checkLocation(Property property, LocationRequest location) {
        if (property.getLatitude() == null || property.getLongitude() == null) {
            return new GeoCheck(null, null);
        }
        int distance = GeoDistance.metersBetween(
                property.getLatitude(), property.getLongitude(), location.latitude(), location.longitude());
        int tolerance = property.getGeoToleranceMeters() != null ? property.getGeoToleranceMeters() : defaultToleranceMeters;
        return new GeoCheck(distance, distance <= tolerance);
    }

    private Map<String, Object> snapshot(Shift shift) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guardId", shift.getGuard().getId());
        map.put("propertyId", shift.getProperty().getId());
        map.put("clockInAt", shift.getClockInAt());
        map.put("clockInDistanceMeters", shift.getClockInDistanceMeters());
        map.put("clockInWithinTolerance", shift.getClockInWithinTolerance());
        return map;
    }

    // ── Auto-close job support ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Shift> findOpenShiftsSince(LocalDateTime lookback) {
        return shiftRepository.findOpenShiftsSince(lookback);
    }

    /**
     * Attempts to auto-close one shift. Returns true if the shift was closed,
     * false if skipped (rule 1: already closed; rule 4: no eligible schedule).
     * Runs in its own transaction so a failure on one shift does not roll back
     * others already closed in the same job run.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAutoClose(Shift shift, int graceMinutes) {
        // Rule 1: guard's real clock-out always wins.
        if (shift.getClockOutAt() != null) {
            return false;
        }

        LocalDate shiftDate = shift.getClockInAt().toLocalDate();
        Optional<ShiftSchedule> scheduleOpt = shiftScheduleRepository.findByGuard_IdAndShiftDateAndDeletedAtIsNull(
                shift.getGuard().getId(), shiftDate);

        // Rule 4: no roster row, no auto-close.
        if (scheduleOpt.isEmpty()) {
            return false;
        }
        ShiftSchedule schedule = scheduleOpt.get();
        if (schedule.getEndTime() == null || schedule.getStartTime() == null) {
            return false;
        }

        // Night-shift midnight-crossing rule: when end_time <= start_time, the
        // effective end is on shift_date + 1 (a 24-h "ends when started" edge
        // case also lands here and is treated the same way).
        LocalDate endDate = schedule.getEndTime().compareTo(schedule.getStartTime()) <= 0
                ? shiftDate.plusDays(1)
                : shiftDate;

        ZoneId zoneId = ZoneId.of(shift.getProperty().getTimezone());
        ZonedDateTime effectiveEnd = ZonedDateTime.of(endDate, schedule.getEndTime(), zoneId);
        ZonedDateTime threshold = effectiveEnd.plusMinutes(graceMinutes);

        if (!ZonedDateTime.now(zoneId).isAfter(threshold)) {
            return false;
        }

        // Rule 2: set clock_out_at to the rostered end time, not when the job ran.
        LocalDateTime clockOutAt = effectiveEnd.toLocalDateTime();
        shift.setClockOutAt(clockOutAt);
        shift.setClockOutSource(ClockOutSource.ROSTER_AUTO_CLOSED);

        Shift saved = shiftRepository.save(shift);

        // Rule 5: actor is null — the system, not the guard.
        auditLogService.record("shift", saved.getId(), AuditAction.UPDATE, null,
                Map.of("clockOutAt", "null"),
                Map.of("clockOutAt", saved.getClockOutAt(), "clockOutSource", ClockOutSource.ROSTER_AUTO_CLOSED.name()));

        return true;
    }

    // ── Supervisor shift list ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ShiftSummaryResponse> listForProperty(Long callerUserId, Long propertyId) {
        assertCanAccessProperty(callerUserId, propertyId);
        List<Shift> shifts = shiftRepository.findByPropertyIdOrderByClockInAtDesc(
                propertyId, PageRequest.of(0, 50));

        List<ShiftSummaryResponse> result = new ArrayList<>(shifts.size());
        for (Shift shift : shifts) {
            int ordinal = 0;
            if (shift.getClockOutSource() == ClockOutSource.ROSTER_AUTO_CLOSED) {
                LocalDateTime weekStart = shift.getClockInAt()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .toLocalDate().atStartOfDay();
                long count = shiftRepository.countAutoClosedInWeekUpTo(
                        shift.getGuard().getId(),
                        ClockOutSource.ROSTER_AUTO_CLOSED,
                        weekStart,
                        shift.getClockInAt());
                ordinal = (int) count;
            }
            result.add(ShiftSummaryResponse.from(shift, ordinal));
        }
        return result;
    }

    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        boolean isAnySupervisor = propertySupervisorRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnySupervisor && !propertySupervisorRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
    }
}
