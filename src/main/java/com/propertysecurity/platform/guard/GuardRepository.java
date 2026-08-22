package com.propertysecurity.platform.guard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuardRepository extends JpaRepository<Guard, Long> {

    List<Guard> findAllByDeletedAtIsNull();

    Optional<Guard> findByIdAndDeletedAtIsNull(Long id);

    Optional<Guard> findByUser_IdAndDeletedAtIsNull(Long userId);
}
