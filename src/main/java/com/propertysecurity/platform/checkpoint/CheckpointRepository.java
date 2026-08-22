package com.propertysecurity.platform.checkpoint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckpointRepository extends JpaRepository<Checkpoint, Long> {

    Optional<Checkpoint> findByIdAndDeletedAtIsNull(Long id);

    Optional<Checkpoint> findByQrTokenAndDeletedAtIsNull(String qrToken);

    List<Checkpoint> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    List<Checkpoint> findAllByProperty_IdAndDeletedAtIsNull(Long propertyId);
}
