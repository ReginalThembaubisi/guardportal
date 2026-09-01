package com.propertysecurity.platform.shift;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByGuard_IdAndClockOutAtIsNull(Long guardId);

    @Query("select s from Shift s left join fetch s.property where s.guard.id = :guardId and s.clockOutAt is null")
    Optional<Shift> findOpenByGuardIdFetchProperty(Long guardId);

    /** Auto-close job: all open shifts within the lookback window, with guard and property pre-fetched. */
    @Query("select s from Shift s join fetch s.guard g join fetch g.property where s.clockOutAt is null and s.clockInAt >= :lookback")
    List<Shift> findOpenShiftsSince(@Param("lookback") LocalDateTime lookback);

    /** Shift list for supervisors/admins, ordered newest first. */
    @Query("select s from Shift s join fetch s.guard g join fetch g.user join fetch g.property where s.property.id = :propertyId order by s.clockInAt desc")
    List<Shift> findByPropertyIdOrderByClockInAtDesc(@Param("propertyId") Long propertyId, Pageable pageable);

    /** Ordinal of this auto-closed shift among the guard's ROSTER_AUTO_CLOSED shifts in the same ISO week. */
    @Query("select count(s) from Shift s where s.guard.id = :guardId and s.clockOutSource = :source and s.clockInAt >= :weekStart and s.clockInAt <= :thisClockInAt")
    long countAutoClosedInWeekUpTo(@Param("guardId") Long guardId, @Param("source") ClockOutSource source, @Param("weekStart") LocalDateTime weekStart, @Param("thisClockInAt") LocalDateTime thisClockInAt);

    /** Coverage report: shifts for a property where clock_in_at falls within the window, guard data fetched. */
    @Query("select s from Shift s join fetch s.guard g join fetch g.user where s.property.id = :propertyId and s.clockInAt >= :from and s.clockInAt < :to")
    List<Shift> findByPropertyAndClockInRange(@Param("propertyId") Long propertyId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
