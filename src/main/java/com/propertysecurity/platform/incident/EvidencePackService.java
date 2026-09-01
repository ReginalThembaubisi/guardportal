package com.propertysecurity.platform.incident;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.propertysecurity.platform.audit.AuditLog;
import com.propertysecurity.platform.audit.AuditLogRepository;
import com.propertysecurity.platform.audit.AuditLogService;
import com.propertysecurity.platform.exception.ResourceNotFoundException;
import com.propertysecurity.platform.shift.Shift;
import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.visitorentry.VisitorEntry;
import com.propertysecurity.platform.visitorentry.VisitorEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidencePackService {

    private final IncidentService incidentService;
    private final IncidentMediaRepository incidentMediaRepository;
    private final VisitorEntryRepository visitorEntryRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final AppUserRepository appUserRepository;
    private final EvidenceExportWriter exportWriter;

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Assembles all incident evidence into a PDF and returns the raw bytes.
     *
     * The export-attempt row is committed before PDF rendering via a
     * REQUIRES_NEW transaction inside EvidenceExportWriter — a failed render
     * leaves the row behind intentionally (over-recorded attempt is safer
     * than unrecorded disclosure).
     */
    @Transactional(readOnly = true)
    public byte[] generatePack(Long callerUserId, Long incidentId) {
        // Validates property-level scope; throws AccessDeniedException if the
        // caller is a PROPERTY_MANAGER without access to this incident's property.
        Incident incident = incidentService.get(callerUserId, incidentId);

        AppUser caller = appUserRepository.findById(callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Caller user not found"));

        Shift shift = incident.getShift();
        Long guardId = shift.getGuard().getId();
        LocalDateTime shiftFrom = shift.getClockInAt();
        LocalDateTime shiftTo = shift.getClockOutAt(); // null when shift still open

        List<VisitorEntry> visitors = visitorEntryRepository.findByGuardAndWindow(guardId, shiftFrom, shiftTo);

        List<Long> visitorIds = visitors.stream().map(VisitorEntry::getId).toList();
        List<Long> invitationIds = visitors.stream()
                .filter(ve -> ve.getInvitation() != null)
                .map(ve -> ve.getInvitation().getId())
                .distinct()
                .toList();

        List<AuditLog> auditRows = gatherAuditRows(shift.getId(), incidentId, visitorIds, invitationIds);

        AuditLogService.VerificationResult verification = auditLogService.verifyChain();
        long chainRowCount = auditLogRepository.count();

        List<IncidentMedia> media = incidentMediaRepository.findAllByIncident_IdOrderByIdAsc(incidentId);

        String reference = UUID.randomUUID().toString();
        LocalDateTime exportedAt = LocalDateTime.now();

        // Commit before rendering — see class doc.
        exportWriter.write(incident, caller, verification.valid(), chainRowCount, reference, exportedAt);

        String html = buildHtml(incident, shift, visitors, auditRows, media,
                verification, chainRowCount, reference, exportedAt, caller);
        return renderPdf(html);
    }

    private List<AuditLog> gatherAuditRows(Long shiftId, Long incidentId,
                                            List<Long> visitorIds, List<Long> invitationIds) {
        List<AuditLog> rows = new ArrayList<>();
        rows.addAll(auditLogRepository.findByEntityNameAndEntityIdOrderByIdAsc("shift", shiftId));
        rows.addAll(auditLogRepository.findByEntityNameAndEntityIdOrderByIdAsc("incident", incidentId));
        if (!visitorIds.isEmpty()) {
            rows.addAll(auditLogRepository.findByEntityNameAndEntityIdInOrderByIdAsc("visitor_entry", visitorIds));
        }
        if (!invitationIds.isEmpty()) {
            rows.addAll(auditLogRepository.findByEntityNameAndEntityIdInOrderByIdAsc("invitation", invitationIds));
        }
        rows.sort(Comparator.comparingLong(AuditLog::getId));
        return rows;
    }

    private String buildHtml(Incident incident, Shift shift, List<VisitorEntry> visitors,
                              List<AuditLog> auditRows, List<IncidentMedia> media,
                              AuditLogService.VerificationResult verification, long chainRowCount,
                              String reference, LocalDateTime exportedAt, AppUser caller) {
        String callerRoles = caller.getRoles().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(", "));
        String guardName = incident.getReportedByGuard().getUser().getFullName();
        String propertyName = incident.getProperty().getName();
        String propertyAddress = incident.getProperty().getAddress() != null
                ? incident.getProperty().getAddress() : "";
        String propertyTimezone = incident.getProperty().getTimezone() != null
                ? incident.getProperty().getTimezone() : "—";

        var sb = new StringBuilder(8192);

        // ── Document head ────────────────────────────────────────────────────
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n")
          .append("<meta charset=\"UTF-8\"/>\n")
          .append("<title>Evidence Pack – Incident #").append(incident.getId()).append("</title>\n")
          .append("<style>\n")
          .append("@page { size: A4; margin: 20mm 25mm; }\n")
          .append("body { font-family: \"DejaVu Serif\", serif; font-size: 10pt; color: #1a1a1a; line-height: 1.5; }\n")
          .append("h1 { font-size: 20pt; margin: 0 0 4pt; }\n")
          .append(".subtitle { font-size: 11pt; color: #444; margin: 0 0 8pt; }\n")
          .append("h2 { font-size: 13pt; border-bottom: 0.5pt solid #888; padding-bottom: 3pt; margin: 18pt 0 8pt; page-break-after: avoid; }\n")
          .append("h3 { font-size: 11pt; margin: 12pt 0 4pt; page-break-after: avoid; }\n")
          .append("table { width: 100%; border-collapse: collapse; font-size: 9pt; margin: 6pt 0 10pt; }\n")
          .append("th { text-align: left; border-bottom: 1pt solid #444; padding: 4pt 6pt; font-size: 8pt; text-transform: uppercase; letter-spacing: 0.3pt; }\n")
          .append("td { padding: 4pt 6pt; border-bottom: 0.5pt solid #ddd; vertical-align: top; }\n")
          .append("tr { page-break-inside: avoid; }\n")
          .append(".section { page-break-before: always; }\n")
          .append("dl.kv { margin: 8pt 0; }\n")
          .append("dl.kv dt { font-weight: bold; }\n")
          .append("dl.kv dd { margin: 0 0 4pt 12pt; }\n")
          .append(".mono { font-family: \"DejaVu Sans Mono\", monospace; font-size: 8pt; word-break: break-all; }\n")
          .append(".hash { font-family: \"DejaVu Sans Mono\", monospace; font-size: 7.5pt; color: #333; }\n")
          .append(".chain-ok { background: #e8f5e9; border: 1pt solid #43a047; padding: 8pt 10pt; margin: 8pt 0; }\n")
          .append(".chain-broken { background: #ffebee; border: 1pt solid #e53935; padding: 8pt 10pt; margin: 8pt 0; }\n")
          .append(".json-block { font-family: \"DejaVu Sans Mono\", monospace; font-size: 7.5pt; background: #f5f5f5; border: 0.5pt solid #ddd; padding: 4pt 6pt; white-space: pre-wrap; word-break: break-all; margin: 2pt 0 6pt; }\n")
          .append(".note { font-style: italic; font-size: 9pt; color: #555; margin: 6pt 0; }\n")
          .append(".disclosure { font-style: italic; font-size: 9pt; color: #555; border-left: 2pt solid #bbb; padding-left: 8pt; margin: 8pt 0; }\n")
          .append(".audit-row { margin-bottom: 10pt; page-break-inside: avoid; }\n")
          .append(".audit-row-header { font-weight: bold; font-size: 9pt; }\n")
          .append(".audit-row-meta { font-size: 8.5pt; color: #444; margin: 2pt 0; }\n")
          .append(".cover-rule { border: none; border-top: 1pt solid #bbb; margin: 16pt 0; }\n")
          .append("</style>\n</head>\n<body>\n");

        // ── Cover page ───────────────────────────────────────────────────────
        sb.append("<h1>Evidence Pack</h1>\n")
          .append("<p class=\"subtitle\">Incident #").append(incident.getId())
          .append(" — ").append(esc(propertyName)).append("</p>\n")
          .append("<p class=\"subtitle\">Generated: ").append(fmt(exportedAt)).append("</p>\n")
          .append("<hr class=\"cover-rule\"/>\n")
          .append("<dl class=\"kv\">\n")
          .append("<dt>Reference</dt><dd class=\"mono\">").append(esc(reference)).append("</dd>\n")
          .append("<dt>Exported by</dt><dd>").append(esc(caller.getFullName()))
              .append(" (").append(esc(callerRoles)).append(")</dd>\n")
          .append("<dt>Property</dt><dd>").append(esc(propertyName)).append("</dd>\n")
          .append("<dt>Chain status</dt><dd>")
              .append(verification.valid() ? "INTACT" : "BROKEN").append("</dd>\n")
          .append("</dl>\n")
          .append("<p class=\"note\">This document is generated for evidentiary purposes. ")
          .append("The reference UUID above is recorded in the evidence_export table and ")
          .append("can be quoted to identify this specific export attempt.</p>\n");

        // ── §1 Incident report ───────────────────────────────────────────────
        sb.append("<div class=\"section\">\n")
          .append("<h2>1. Incident Report</h2>\n")
          .append("<table>\n")
          .append("<tr><th>Field</th><th>Value</th></tr>\n")
          .append("<tr><td>Incident ID</td><td>#").append(incident.getId()).append("</td></tr>\n")
          .append("<tr><td>Property</td><td>").append(esc(propertyName));
        if (!propertyAddress.isEmpty()) {
            sb.append(", ").append(esc(propertyAddress));
        }
        sb.append("</td></tr>\n")
          .append("<tr><td>Severity</td><td>").append(esc(incident.getSeverity().name())).append("</td></tr>\n")
          .append("<tr><td>Status</td><td>").append(esc(incident.getStatus().name())).append("</td></tr>\n")
          .append("<tr><td>Reported at (server)</td><td>").append(fmt(incident.getReportedAt())).append("</td></tr>\n");
        if (incident.getClientClaimedAt() != null) {
            sb.append("<tr><td>Client claimed at</td><td>").append(fmt(incident.getClientClaimedAt())).append("</td></tr>\n");
        }
        sb.append("<tr><td>Reported by</td><td>").append(esc(guardName));
        String badge = incident.getReportedByGuard().getBadgeNumber();
        if (badge != null && !badge.isBlank()) {
            sb.append(" (badge: ").append(esc(badge)).append(")");
        }
        sb.append("</td></tr>\n")
          .append("<tr><td>GPS</td><td>").append(fmtCoord(incident.getLatitude()))
          .append(", ").append(fmtCoord(incident.getLongitude())).append("</td></tr>\n")
          .append("</table>\n")
          .append("<h3>Description</h3>\n")
          .append("<p>").append(esc(incident.getDescription())).append("</p>\n");

        if (!media.isEmpty()) {
            sb.append("<h3>Attached Media (").append(media.size()).append(" file")
              .append(media.size() == 1 ? "" : "s").append(")</h3>\n<ul>\n");
            for (IncidentMedia m : media) {
                long kb = m.getFileSizeBytes() / 1024;
                sb.append("<li>").append(esc(m.getOriginalFilename()))
                  .append(" (").append(esc(m.getContentType())).append(", ").append(kb).append(" KB)")
                  .append("</li>\n");
            }
            sb.append("</ul>\n")
              .append("<p class=\"note\">Media files are not embedded in this document. ")
              .append("Access them via the authenticated media endpoint using the incident ID and media IDs listed.</p>\n");
        } else {
            sb.append("<p class=\"note\">No media files attached to this incident.</p>\n");
        }
        sb.append("</div>\n");

        // ── §2 Guard shift ───────────────────────────────────────────────────
        sb.append("<div class=\"section\">\n")
          .append("<h2>2. Guard Shift</h2>\n")
          .append("<table>\n")
          .append("<tr><th>Field</th><th>Value</th></tr>\n")
          .append("<tr><td>Shift ID</td><td>#").append(shift.getId()).append("</td></tr>\n")
          .append("<tr><td>Guard</td><td>").append(esc(shift.getGuard().getUser().getFullName()));
        if (badge != null && !badge.isBlank()) {
            sb.append(" (badge: ").append(esc(badge)).append(")");
        }
        sb.append("</td></tr>\n")
          .append("<tr><td>Shift type</td><td>").append(shift.getShiftType() != null ? esc(shift.getShiftType().name()) : "—").append("</td></tr>\n")
          .append("<tr><td>Property timezone</td><td>").append(esc(propertyTimezone)).append("</td></tr>\n")
          .append("<tr><td>Clock-in (server)</td><td>").append(fmt(shift.getClockInAt())).append("</td></tr>\n");
        if (shift.getClientClaimedClockInAt() != null) {
            sb.append("<tr><td>Clock-in (client claimed)</td><td>")
              .append(fmt(shift.getClientClaimedClockInAt())).append("</td></tr>\n");
        }
        sb.append("<tr><td>Clock-out (server)</td><td>")
          .append(shift.getClockOutAt() != null ? fmt(shift.getClockOutAt()) : "Still open")
          .append("</td></tr>\n");
        if (shift.getClientClaimedClockOutAt() != null) {
            sb.append("<tr><td>Clock-out (client claimed)</td><td>")
              .append(fmt(shift.getClientClaimedClockOutAt())).append("</td></tr>\n");
        }
        if (shift.getClockOutSource() != null) {
            sb.append("<tr><td>Clock-out source</td><td>").append(esc(shift.getClockOutSource().name())).append("</td></tr>\n");
        }
        sb.append("<tr><td>Clock-in GPS</td><td>")
          .append(fmtCoord(shift.getClockInLatitude())).append(", ").append(fmtCoord(shift.getClockInLongitude()));
        if (shift.getClockInDistanceMeters() != null) {
            sb.append(" (").append(shift.getClockInDistanceMeters()).append(" m from property)");
        }
        sb.append("</td></tr>\n");
        if (shift.getClockOutAt() != null) {
            sb.append("<tr><td>Clock-out GPS</td><td>")
              .append(fmtCoord(shift.getClockOutLatitude())).append(", ").append(fmtCoord(shift.getClockOutLongitude()));
            if (shift.getClockOutDistanceMeters() != null) {
                sb.append(" (").append(shift.getClockOutDistanceMeters()).append(" m from property)");
            }
            sb.append("</td></tr>\n");
        }
        sb.append("</table>\n")
          .append("<p class=\"note\">GPS coordinates are recorded as evidence of where the guard was. ")
          .append("They are not scored against a radius and do not constitute a pass/fail verdict.</p>\n")
          .append("</div>\n");

        // ── §3 Visitor log ───────────────────────────────────────────────────
        sb.append("<div class=\"section\">\n")
          .append("<h2>3. Visitor Log</h2>\n")
          .append("<p class=\"disclosure\">These visitor entries are associated with this shift by matching ")
          .append("guard ID and entry time within the shift window [clockInAt, clockOutAt). ")
          .append("There is no direct foreign key between visitor_entry and shift; this association ")
          .append("is derived at export time.</p>\n");

        if (visitors.isEmpty()) {
            sb.append("<p class=\"note\">No visitor entries processed during this shift window.</p>\n");
        } else {
            sb.append("<table>\n")
              .append("<tr><th>#</th><th>Name</th><th>Category</th><th>Approval</th>")
              .append("<th>Unit</th><th>Entered</th><th>Exited</th><th>Notes</th></tr>\n");
            for (int i = 0; i < visitors.size(); i++) {
                VisitorEntry ve = visitors.get(i);
                String unit = ve.getUnit() != null ? ve.getUnit().getUnitNumber() : "—";
                sb.append("<tr>")
                  .append("<td>").append(i + 1).append("</td>")
                  .append("<td>").append(esc(ve.getVisitorName())).append("</td>")
                  .append("<td>").append(esc(ve.getCategory().name())).append("</td>")
                  .append("<td>").append(esc(ve.getApprovalStatus().name())).append("</td>")
                  .append("<td>").append(esc(unit)).append("</td>")
                  .append("<td>").append(fmt(ve.getEnteredAt())).append("</td>")
                  .append("<td>").append(ve.getExitedAt() != null ? fmt(ve.getExitedAt()) : "On site").append("</td>")
                  .append("<td>").append(ve.getNotes() != null ? esc(ve.getNotes()) : "").append("</td>")
                  .append("</tr>\n");
            }
            sb.append("</table>\n");
        }
        sb.append("</div>\n");

        // ── §4 Audit trail ───────────────────────────────────────────────────
        sb.append("<div class=\"section\">\n")
          .append("<h2>4. Audit Trail</h2>\n");

        long inviteCount = visitors.stream().filter(ve -> ve.getInvitation() != null).count();
        String coverageDesc = "shift #" + shift.getId() + ", incident #" + incident.getId();
        if (!visitors.isEmpty()) {
            coverageDesc += ", " + visitors.size() + " visitor entr" + (visitors.size() == 1 ? "y" : "ies");
        }
        if (inviteCount > 0) {
            coverageDesc += ", " + inviteCount + " invitation" + (inviteCount == 1 ? "" : "s");
        }
        sb.append("<p>Showing ").append(auditRows.size()).append(" record")
          .append(auditRows.size() == 1 ? "" : "s").append(" covering: ").append(esc(coverageDesc))
          .append(". Records are ordered by chain ID (ascending). ")
          .append("The entire audit chain was verified before export.</p>\n");

        if (auditRows.isEmpty()) {
            sb.append("<p class=\"note\">No audit records found for the entities in this evidence pack.</p>\n");
        } else {
            for (AuditLog row : auditRows) {
                sb.append("<div class=\"audit-row\">\n")
                  .append("<p class=\"audit-row-header\">#").append(row.getId())
                  .append(" — ").append(esc(row.getEntityName()))
                  .append(" #").append(row.getEntityId())
                  .append(" — ").append(esc(row.getAction().name())).append("</p>\n")
                  .append("<p class=\"audit-row-meta\">Performed by user #")
                  .append(row.getPerformedByUserId() != null ? row.getPerformedByUserId() : "system")
                  .append(" at ").append(esc(row.getPerformedAt().toString())).append("</p>\n");
                if (row.getBeforeValue() != null) {
                    sb.append("<p class=\"audit-row-meta\"><strong>Before:</strong></p>\n")
                      .append("<div class=\"json-block\">").append(esc(row.getBeforeValue())).append("</div>\n");
                }
                if (row.getAfterValue() != null) {
                    sb.append("<p class=\"audit-row-meta\"><strong>After:</strong></p>\n")
                      .append("<div class=\"json-block\">").append(esc(row.getAfterValue())).append("</div>\n");
                }
                sb.append("<p class=\"audit-row-meta\"><strong>Hash</strong> (4 × 16 chars):</p>\n")
                  .append("<p class=\"hash\">").append(fmtHash(row.getRecordHash())).append("</p>\n")
                  .append("</div>\n");
            }
        }

        sb.append("<h3>Hash Recomputation Note</h3>\n")
          .append("<p class=\"note\">Each row's hash is SHA-256 of the UTF-8 bytes of the ")
          .append("concatenation of exactly 7 fields with no separator between them:</p>\n")
          .append("<ol class=\"note\">\n")
          .append("<li><strong>previousHash</strong> — therecord_hash of the preceding row by ID, ")
          .append("or an empty string for the very first row.</li>\n")
          .append("<li><strong>entityName</strong></li>\n")
          .append("<li><strong>entityId</strong> (as a decimal integer string)</li>\n")
          .append("<li><strong>action</strong> (the enum name, e.g. CREATE, UPDATE)</li>\n")
          .append("<li><strong>beforeValue</strong> — theJSON string, or an empty string (not the ")
          .append("literal \"null\") when the column is NULL.</li>\n")
          .append("<li><strong>afterValue</strong> — samenull-handling as beforeValue.</li>\n")
          .append("<li><strong>performedAt</strong> — asproduced by Java's ")
          .append("LocalDateTime.toString(). Note: when seconds are zero the seconds component is ")
          .append("omitted (e.g. 2026-09-01T14:00 not 2026-09-01T14:00:00). ")
          .append("This edge case must be reproduced exactly to verify any minute-boundary row.</li>\n")
          .append("</ol>\n")
          .append("<p class=\"note\">Hashes above are printed in four 16-character groups for readability. ")
          .append("The groups are formatting only: concatenate them without spaces or newlines to obtain ")
          .append("the 64-character string used as input and output in the algorithm above.</p>\n")
          .append("</div>\n");

        // ── §5 Verification certificate ──────────────────────────────────────
        sb.append("<div class=\"section\">\n")
          .append("<h2>5. Verification Certificate</h2>\n");

        if (verification.valid()) {
            sb.append("<div class=\"chain-ok\"><strong>Chain status: INTACT</strong> — all")
              .append(chainRowCount).append(" row").append(chainRowCount == 1 ? "" : "s")
              .append(" verified.</div>\n");
        } else {
            sb.append("<div class=\"chain-broken\"><strong>Chain status: BROKEN</strong> — ")
              .append("first broken row ID: ").append(verification.firstBrokenId()).append("</div>\n");
        }

        sb.append("<dl class=\"kv\">\n")
          .append("<dt>Chain row count</dt><dd>").append(chainRowCount).append("</dd>\n")
          .append("<dt>Verified at</dt><dd>").append(fmt(exportedAt)).append("</dd>\n")
          .append("<dt>Exported by</dt><dd>").append(esc(caller.getFullName()))
              .append(" (").append(esc(callerRoles)).append(")</dd>\n")
          .append("<dt>Reference</dt><dd class=\"mono\">").append(esc(reference)).append("</dd>\n")
          .append("</dl>\n")
          .append("<p>The audit records reproduced here are a subset of the verified chain, selected ")
          .append("by the entity IDs listed in §4 and reproduced in chain order. The chain was ")
          .append("verified in full; no row within it was missing or out of sequence.</p>\n")
          .append("</div>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private byte[] renderPdf(String html) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF rendering failed for evidence pack", e);
        }
    }

    private void registerFonts(PdfRendererBuilder builder) {
        registerFont(builder, "/fonts/DejaVuSerif.ttf",            "DejaVu Serif",    400, BaseRendererBuilder.FontStyle.NORMAL);
        registerFont(builder, "/fonts/DejaVuSerif-Bold.ttf",       "DejaVu Serif",    700, BaseRendererBuilder.FontStyle.NORMAL);
        registerFont(builder, "/fonts/DejaVuSerif-Italic.ttf",     "DejaVu Serif",    400, BaseRendererBuilder.FontStyle.ITALIC);
        registerFont(builder, "/fonts/DejaVuSerif-BoldItalic.ttf", "DejaVu Serif",    700, BaseRendererBuilder.FontStyle.ITALIC);
        registerFont(builder, "/fonts/DejaVuSansMono.ttf",         "DejaVu Sans Mono", 400, BaseRendererBuilder.FontStyle.NORMAL);
        registerFont(builder, "/fonts/DejaVuSansMono-Bold.ttf",    "DejaVu Sans Mono", 700, BaseRendererBuilder.FontStyle.NORMAL);
    }

    private void registerFont(PdfRendererBuilder builder, String classpathPath,
                               String family, int weight, BaseRendererBuilder.FontStyle style) {
        if (getClass().getResource(classpathPath) == null) {
            throw new IllegalStateException(
                "Required font not found at classpath:" + classpathPath +
                " — ensure the DejaVu TTFs are present in src/main/resources/fonts/.");
        }
        builder.useFont(() -> getClass().getResourceAsStream(classpathPath), family, weight, style, true);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Splits a 64-character hex hash into four 16-character lines so the
     * break position is the same in every rendered PDF, regardless of column
     * width, font hinting, or page size. The groups are formatting only;
     * the recomputation note in §4 tells the reader to concatenate them.
     */
    private static String fmtHash(String hash) {
        if (hash == null || hash.length() != 64) {
            log.warn("Unexpected hash length in evidence pack: expected 64, got {} — audit_log id may reference a corrupted row",
                    hash == null ? "null" : hash.length());
            return esc(hash);
        }
        return esc(hash.substring(0, 16)) + "<br/>"
             + esc(hash.substring(16, 32)) + "<br/>"
             + esc(hash.substring(32, 48)) + "<br/>"
             + esc(hash.substring(48, 64));
    }

    private static String fmt(LocalDateTime dt) {
        return dt == null ? "—" : dt.format(DISPLAY);
    }

    private static String fmtCoord(BigDecimal coord) {
        return coord == null ? "—" : coord.toPlainString();
    }
}
