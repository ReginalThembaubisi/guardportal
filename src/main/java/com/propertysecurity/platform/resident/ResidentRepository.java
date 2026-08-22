package com.propertysecurity.platform.resident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    List<Resident> findAllByDeletedAtIsNull();

    Optional<Resident> findByIdAndDeletedAtIsNull(Long id);
}
