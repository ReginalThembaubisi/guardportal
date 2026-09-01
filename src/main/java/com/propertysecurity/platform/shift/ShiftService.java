package com.propertysecurity.platform.shift;

import com.propertysecurity.platform.audit.AuditAction;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.common.GeoDistance;
import com.propertysecurity.platform.exception.BadRequestException;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.shift.dto.LocationRequest;
import com.propertysecurity.platform.shiftschedule.ShiftSchedule;
import com.propertysecurity.platform.shiftschedule.ShiftScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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

        LocalDateTime clockInAt = LocalDateTime.now();

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

        LocalDateTime clockOutAt = LocalDateTime.now();
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
}
