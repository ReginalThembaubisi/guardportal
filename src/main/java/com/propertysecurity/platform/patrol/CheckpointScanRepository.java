package com.propertysecurity.platform.patrol;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface CheckpointScanRepository extends JpaRepository<CheckpointScan, Long> {

    @Query("select s from CheckpointScan s " +
            "where s.checkpoint.id in :checkpointIds and s.scannedAt between :from and :to " +
            "order by s.scannedAt asc")
    List<CheckpointScan> findAllByCheckpointIdInAndScannedAtBetween(
            List<Long> checkpointIds, LocalDateTime from, LocalDateTime to);
}
