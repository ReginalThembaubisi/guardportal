package com.propertysecurity.platform.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    Optional<Shift> findByGuard_IdAndClockOutAtIsNull(Long guardId);

    @Query("select s from Shift s left join fetch s.property where s.guard.id = :guardId and s.clockOutAt is null")
    Optional<Shift> findOpenByGuardIdFetchProperty(Long guardId);
}
