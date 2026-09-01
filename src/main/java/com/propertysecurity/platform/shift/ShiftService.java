package com.propertysecurity.platform.shift;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.common.GeoDistance;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.propertymanager.PropertyManagerRepository;
import com.propertysecurity.platform.propertysupervisor.PropertySupervisorRepository;
import com.propertysecurity.platform.shift.dto.LocationRequest;
import com.propertysecurity.platform.shift.dto.ShiftCoverageSlot;
import com.propertysecurity.platform.shift.dto.ShiftSummaryResponse;
import com.propertysecurity.platform.shiftschedule.ShiftSchedule;
import com.propertysecurity.platform.shiftschedule.ShiftScheduleRepository;
import com.propertysecurity.platform.shiftschedule.ShiftScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * Clock-in/out coordinates are recorded as evidence of where the guard was,
 * not scored against a radius. Guards are dropped at the property and clock in
 * on arrival; the coordinate is the record. Patrol checkpoint scans are the
 * exception and still compare distance — see PatrolService.
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
    private final PropertyManagerRepository propertyManagerRepository;

    // Fallback when a property has no timezone set. All operational properties should
    // have this field populated at creation; this guards new rows and any legacy gap.
    @Value("${app.shift.timezone:Africa/Johannesburg}")
    private String defaultShiftTimezone;

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
        ZoneId propertyZone = zoneFor(property);
        LocalDateTime clockInAt = LocalDateTime.now(propertyZone);

        Shift shift = new Shift();
        shift.setGuard(guard);
        shift.setProperty(property);
        shift.setClockInAt(clockInAt);
        shift.setClientClaimedClockInAt(validatedClaim(location.clientClaimedAt(), null, clockInAt, guardUserId, "clock-in"));
        shift.setClockInLatitude(location.latitude());
        shift.setClockInLongitude(location.longitude());
        shift.setClockInDistanceMeters(check.distanceMeters());
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

        LocalDateTime clockOutAt = LocalDateTime.now(zoneFor(shift.getProperty()));
        LocalDateTime claimedClockOutAt = validatedClaim(location.clientClaimedAt(), shift.getClockInAt(), clockOutAt, guardUserId, "clock-out");
        shift.setClockOutAt(clockOutAt);
        shift.setClientClaimedClockOutAt(claimedClockOutAt);
        shift.setClockOutLatitude(location.latitude());
        shift.setClockOutLongitude(location.longitude());
        shift.setClockOutDistanceMeters(check.distanceMeters());

        ClockOutSource source = null;
        if (claimedClockOutAt != null && Duration.between(claimedClockOutAt, clockOutAt).compareTo(CLOCK_OUT_STALENESS_THRESHOLD) > 0) {
            source = ClockOutSource.CLIENT_CLAIMED_LATE;
        }
        shift.setClockOutSource(source);

        Shift saved = shiftRepository.save(shift);
        auditLogService.record("shift", saved.getId(), AuditAction.UPDATE, guardUserId,
                Map.of("clockOutAt", "null"),
                Map.of("clockOutAt", saved.getClockOutAt()));
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

    private ZoneId zoneFor(Property p) {
        String tz = p.getTimezone();
        return ZoneId.of((tz != null && !tz.isBlank()) ? tz : defaultShiftTimezone);
    }

    private record GeoCheck(Integer distanceMeters) {}

    /** Null distance when the property has no known location yet. */
    private GeoCheck checkLocation(Property property, LocationRequest location) {
        if (property.getLatitude() == null || property.getLongitude() == null) {
            return new GeoCheck(null);
        }
        int distance = GeoDistance.metersBetween(
                property.getLatitude(), property.getLongitude(), location.latitude(), location.longitude());
        return new GeoCheck(distance);
    }

    private Map<String, Object> snapshot(Shift shift) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("guardId", shift.getGuard().getId());
        map.put("propertyId", shift.getProperty().getId());
        map.put("clockInAt", shift.getClockInAt());
        map.put("clockInDistanceMeters", shift.getClockInDistanceMeters());
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

        ZoneId zoneId = zoneFor(shift.getProperty());
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
    public List<ShiftSummaryResponse> listForProperty(Long callerUserId, Long propertyId, ShiftFlag flag) {
        assertCanAccessProperty(callerUserId, propertyId);
        Pageable page = PageRequest.of(0, 50);
        List<Shift> shifts = switch (flag) {
            case FLAGGED       -> shiftRepository.findFlaggedByPropertyIdOrderByClockInAtDesc(propertyId, page);
            case LATE_CLOCKOUT -> shiftRepository.findByPropertyIdAndClockOutSourceOrderByClockInAtDesc(propertyId, ClockOutSource.CLIENT_CLAIMED_LATE, page);
            case AUTO_CLOSED   -> shiftRepository.findByPropertyIdAndClockOutSourceOrderByClockInAtDesc(propertyId, ClockOutSource.ROSTER_AUTO_CLOSED, page);
            case ALL           -> shiftRepository.findByPropertyIdOrderByClockInAtDesc(propertyId, page);
        };

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

    // ── Coverage report ───────────────────────────────────────────────────────

    // Acceptance window for matching a clock-in to a schedule slot.
    // A guard on an 18:00 night shift who clocks in at 00:30 the next day still
    // falls within 12 hours of the rostered start — using calendar date alone
    // would produce a false NO_SHOW.  The 1-hour early buffer covers guards who
    // arrive just before the rostered start.  When startTime is null the window
    // falls back to the full calendar day.
    private static final int COVERAGE_WINDOW_HOURS_BEFORE = 1;
    private static final int COVERAGE_WINDOW_HOURS_AFTER = 12;

    @Transactional(readOnly = true)
    public List<ShiftCoverageSlot> coverageForProperty(Long callerUserId, Long propertyId, LocalDate from, LocalDate to) {
        assertCanAccessProperty(callerUserId, propertyId);

        List<ShiftSchedule> schedules = shiftScheduleRepository.findByPropertyAndDateRange(propertyId, from, to);

        // Fetch a wider-than-requested shift window so night-shift clock-ins that
        // cross midnight into (to + 1) are still available for per-slot matching.
        LocalDateTime fetchFrom = from.atStartOfDay().minusHours(COVERAGE_WINDOW_HOURS_BEFORE);
        LocalDateTime fetchTo = to.plusDays(1).atStartOfDay().plusHours(COVERAGE_WINDOW_HOURS_AFTER);
        List<Shift> shifts = shiftRepository.findByPropertyAndClockInRange(propertyId, fetchFrom, fetchTo);

        List<ShiftCoverageSlot> result = new ArrayList<>(schedules.size());
        for (ShiftSchedule schedule : schedules) {
            // Per-slot acceptance window anchored on the rostered start time.
            LocalDateTime slotStart;
            LocalDateTime slotEnd;
            if (schedule.getStartTime() != null) {
                LocalDateTime rostered = schedule.getShiftDate().atTime(schedule.getStartTime());
                slotStart = rostered.minusHours(COVERAGE_WINDOW_HOURS_BEFORE);
                slotEnd   = rostered.plusHours(COVERAGE_WINDOW_HOURS_AFTER);
            } else {
                slotStart = schedule.getShiftDate().atStartOfDay();
                slotEnd   = schedule.getShiftDate().plusDays(1).atStartOfDay();
            }

            // Scan for the best matching shift: same guard, clock-in within window.
            // Latest clock-in wins in the rare case of a re-clock-in after an erroneous auto-close.
            Shift best = null;
            for (Shift s : shifts) {
                if (!s.getGuard().getId().equals(schedule.getGuard().getId())) continue;
                LocalDateTime ci = s.getClockInAt();
                if (ci.isBefore(slotStart) || !ci.isBefore(slotEnd)) continue;
                if (best == null || ci.isAfter(best.getClockInAt())) best = s;
            }

            if (best == null) {
                result.add(ShiftCoverageSlot.noShow(schedule));
            } else if (best.getClockOutAt() == null) {
                result.add(ShiftCoverageSlot.open(schedule, best));
            } else {
                result.add(ShiftCoverageSlot.worked(schedule, best));
            }
        }
        return result;
    }

    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        boolean isAnySupervisor = propertySupervisorRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnySupervisor && !propertySupervisorRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
        boolean isAnyPropertyManager = propertyManagerRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnyPropertyManager && !propertyManagerRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
    }
}
