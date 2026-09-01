package com.propertysecurity.platform.shiftschedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftScheduleRepository extends JpaRepository<ShiftSchedule, Long> {

    boolean existsByGuard_IdAndShiftDateAndDeletedAtIsNull(Long guardId, LocalDate shiftDate);

    @Query("select s from ShiftSchedule s join fetch s.property join fetch s.guard g join fetch g.user where s.guard.id = :guardId and s.shiftDate = :shiftDate and s.deletedAt is null")
    Optional<ShiftSchedule> findByGuard_IdAndShiftDateAndDeletedAtIsNull(@Param("guardId") Long guardId, @Param("shiftDate") LocalDate shiftDate);

    Optional<ShiftSchedule> findByIdAndDeletedAtIsNull(Long id);

    @Query("select s from ShiftSchedule s join fetch s.property join fetch s.guard g join fetch g.user where s.property.id = :propertyId and s.deletedAt is null order by s.shiftDate asc, s.startTime asc")
    List<ShiftSchedule> findAllByProperty_IdAndDeletedAtIsNull(@Param("propertyId") Long propertyId);

    @Query("select s from ShiftSchedule s join fetch s.property join fetch s.guard g join fetch g.user where g.user.id = :guardUserId and s.shiftDate >= :from and s.deletedAt is null order by s.shiftDate asc, s.startTime asc")
    List<ShiftSchedule> findUpcomingForGuardUser(@Param("guardUserId") Long guardUserId, @Param("from") LocalDate from);

    /** Coverage report: all rostered slots for a property within a date range, guard data fetched. */
    @Query("select s from ShiftSchedule s join fetch s.guard g join fetch g.user where s.property.id = :propertyId and s.shiftDate >= :from and s.shiftDate <= :to and s.deletedAt is null order by s.shiftDate asc, g.user.fullName asc")
    List<ShiftSchedule> findByPropertyAndDateRange(@Param("propertyId") Long propertyId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
