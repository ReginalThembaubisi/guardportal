package com.propertysecurity.platform.incident;

import com.propertysecurity.platform.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Records each evidence-pack export attempt. Written before PDF rendering so
 * a failed render still leaves a row — see EvidenceExportWriter for the
 * REQUIRES_NEW transaction that commits this independently of the caller's
 * read-only transaction. Append-only; no deleted_at.
 */
@Entity
@Table(name = "evidence_export")
@Getter
@Setter
@NoArgsConstructor
public class EvidenceExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exported_by_user_id", nullable = false)
    private AppUser exportedByUser;

    @Column(name = "exported_at", nullable = false)
    private LocalDateTime exportedAt;

    @Column(name = "chain_valid", nullable = false)
    private boolean chainValid;

    @Column(name = "chain_row_count", nullable = false)
    private long chainRowCount;

    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;
}
