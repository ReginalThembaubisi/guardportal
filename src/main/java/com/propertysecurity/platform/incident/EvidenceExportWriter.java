package com.propertysecurity.platform.incident;

import com.propertysecurity.platform.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Separate bean so that REQUIRES_NEW propagation correctly suspends the
 * caller's read-only transaction and commits this row independently —
 * a self-invocation on EvidencePackService would bypass the proxy and
 * inherit the outer transaction instead.
 */
@Service
@RequiredArgsConstructor
class EvidenceExportWriter {

    private final EvidenceExportRepository evidenceExportRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    EvidenceExport write(Incident incident, AppUser exportedBy,
                         boolean chainValid, long chainRowCount,
                         String reference, LocalDateTime exportedAt) {
        EvidenceExport export = new EvidenceExport();
        export.setIncident(incident);
        export.setExportedByUser(exportedBy);
        export.setExportedAt(exportedAt);
        export.setChainValid(chainValid);
        export.setChainRowCount(chainRowCount);
        export.setReference(reference);
        return evidenceExportRepository.save(export);
    }
}
