package com.propertysecurity.platform.shiftschedule;

import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.guard.Guard;
import com.propertysecurity.platform.guard.GuardRepository;
import com.propertysecurity.platform.property.Property;
import com.propertysecurity.platform.property.PropertyRepository;
import com.propertysecurity.platform.propertysupervisor.PropertySupervisorRepository;
import com.propertysecurity.platform.shift.ShiftType;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleCreateRequest;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleImportRequest;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleImportResponse;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleImportResultRow;
import com.propertysecurity.platform.shiftschedule.dto.ShiftScheduleImportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Replaces the WhatsApp-group shift-sharing: a Supervisor uploads/enters
 * the roster here, guards read their own upcoming shifts from it, and
 * ShiftService.clockIn looks up today's row to know which shift a clock-in
 * is fulfilling. Same assertCanAccessProperty idiom as GuardService, backed
 * by PropertySupervisorRepository.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ShiftScheduleService {

    private final ShiftScheduleRepository shiftScheduleRepository;
    private final GuardRepository guardRepository;
    private final PropertyRepository propertyRepository;
    private final PropertySupervisorRepository propertySupervisorRepository;

    public ShiftSchedule create(Long callerUserId, ShiftScheduleCreateRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));
        assertCanAccessProperty(callerUserId, property.getId());

        Guard guard = guardRepository.findByIdAndDeletedAtIsNull(request.guardId())
                .orElseThrow(() -> new ResourceNotFoundException("Guard " + request.guardId() + " not found"));
        if (!guard.getProperty().getId().equals(property.getId())) {
            throw new AccessDeniedException("This guard is not assigned to this property");
        }

        ShiftSchedule schedule = new ShiftSchedule();
        schedule.setGuard(guard);
        schedule.setProperty(property);
        schedule.setShiftDate(request.shiftDate());
        schedule.setShiftType(request.shiftType());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        return shiftScheduleRepository.save(schedule);
    }

    /**
     * Bulk roster upload — one call per row instead of one-at-a-time create().
     * Never fails the whole batch over one bad row: each row is validated
     * independently and reported back as created/skipped-with-reason, same
     * as ResidentService.importResidents.
     */
    public ShiftScheduleImportResponse importSchedules(Long callerUserId, ShiftScheduleImportRequest request) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property " + request.propertyId() + " not found"));
        assertCanAccessProperty(callerUserId, property.getId());

        List<ShiftScheduleImportResultRow> results = new ArrayList<>();
        int created = 0;
        int rowNumber = 0;
        for (ShiftScheduleImportRow row : request.rows()) {
            rowNumber++;
            ParsedRow parsed;
            try {
                parsed = parseAndValidateImportRow(row, property);
            } catch (RowRejected rejected) {
                results.add(new ShiftScheduleImportResultRow(rowNumber, row.guardPhoneNumber(), row.shiftDate(), false, rejected.getMessage()));
                continue;
            }

            ShiftSchedule schedule = new ShiftSchedule();
            schedule.setGuard(parsed.guard());
            schedule.setProperty(property);
            schedule.setShiftDate(parsed.shiftDate());
            schedule.setShiftType(parsed.shiftType());
            schedule.setStartTime(parsed.startTime());
            schedule.setEndTime(parsed.endTime());
            shiftScheduleRepository.save(schedule);

            created++;
            results.add(new ShiftScheduleImportResultRow(rowNumber, row.guardPhoneNumber(), row.shiftDate(), true, null));
        }

        return new ShiftScheduleImportResponse(created, results.size() - created, results);
    }

    private record ParsedRow(Guard guard, LocalDate shiftDate, ShiftType shiftType, LocalTime startTime, LocalTime endTime) {
    }

    private static class RowRejected extends RuntimeException {
        RowRejected(String message) {
            super(message);
        }
    }

    private ParsedRow parseAndValidateImportRow(ShiftScheduleImportRow row, Property property) {
        if (row.guardPhoneNumber() == null || row.guardPhoneNumber().isBlank()) {
            throw new RowRejected("Missing guard phone number");
        }
        Guard guard = guardRepository.findByUser_PhoneNumberAndDeletedAtIsNull(row.guardPhoneNumber())
                .orElseThrow(() -> new RowRejected("No guard found with phone number " + row.guardPhoneNumber()));
        if (!guard.getProperty().getId().equals(property.getId())) {
            throw new RowRejected("Guard is not assigned to this property");
        }

        if (row.shiftDate() == null || row.shiftDate().isBlank()) {
            throw new RowRejected("Missing shift date");
        }
        LocalDate shiftDate;
        try {
            shiftDate = LocalDate.parse(row.shiftDate().trim());
        } catch (DateTimeParseException e) {
            throw new RowRejected("Invalid shift date (expected YYYY-MM-DD)");
        }

        if (row.shiftType() == null || row.shiftType().isBlank()) {
            throw new RowRejected("Missing shift type");
        }
        ShiftType shiftType;
        try {
            shiftType = ShiftType.valueOf(row.shiftType().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RowRejected("Invalid shift type (expected DAY or NIGHT)");
        }

        LocalTime startTime = parseOptionalTime(row.startTime(), "Invalid start time (expected HH:mm)");
        LocalTime endTime = parseOptionalTime(row.endTime(), "Invalid end time (expected HH:mm)");

        if (shiftScheduleRepository.existsByGuard_IdAndShiftDateAndDeletedAtIsNull(guard.getId(), shiftDate)) {
            throw new RowRejected("Guard already has a scheduled shift on this date");
        }

        return new ParsedRow(guard, shiftDate, shiftType, startTime, endTime);
    }

    private LocalTime parseOptionalTime(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new RowRejected(errorMessage);
        }
    }

    @Transactional(readOnly = true)
    public List<ShiftSchedule> listForProperty(Long callerUserId, Long propertyId) {
        assertCanAccessProperty(callerUserId, propertyId);
        return shiftScheduleRepository.findAllByProperty_IdAndDeletedAtIsNull(propertyId);
    }

    @Transactional(readOnly = true)
    public List<ShiftSchedule> listUpcomingForGuard(Long guardUserId) {
        return shiftScheduleRepository.findUpcomingForGuardUser(guardUserId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public Optional<ShiftSchedule> findTodayForGuard(Long guardId) {
        return shiftScheduleRepository.findByGuard_IdAndShiftDateAndDeletedAtIsNull(guardId, LocalDate.now());
    }

    /** Same as findTodayForGuard, but starting from the logged-in user's id — for the /today endpoint. */
    @Transactional(readOnly = true)
    public Optional<ShiftSchedule> findTodayForGuardUser(Long guardUserId) {
        Guard guard = guardRepository.findByUser_IdAndDeletedAtIsNull(guardUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No guard profile found for this account"));
        return findTodayForGuard(guard.getId());
    }

    public void remove(Long callerUserId, Long id) {
        ShiftSchedule schedule = shiftScheduleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift schedule " + id + " not found"));
        assertCanAccessProperty(callerUserId, schedule.getProperty().getId());
        schedule.setDeletedAt(LocalDateTime.now());
        shiftScheduleRepository.save(schedule);
    }

    private void assertCanAccessProperty(Long callerUserId, Long propertyId) {
        boolean isAnySupervisor = propertySupervisorRepository.existsByUser_IdAndDeletedAtIsNull(callerUserId);
        if (isAnySupervisor && !propertySupervisorRepository.existsByUser_IdAndProperty_IdAndDeletedAtIsNull(callerUserId, propertyId)) {
            throw new AccessDeniedException("This property is not yours");
        }
    }
}
