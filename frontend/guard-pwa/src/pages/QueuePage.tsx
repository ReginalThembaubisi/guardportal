import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiFetch, ApiError } from "../api/client";
import type { ShiftResponse } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useOfflineQueue } from "../OfflineQueueContext";
import Layout from "../components/Layout";
import type { QueueEntry, QueueEntryStatus, QueueEntryType } from "../offlineQueue";

const TYPE_LABELS: Record<QueueEntryType, string> = {
  CLOCK_OUT: "Clock out",
  CHECKPOINT_SCAN: "Checkpoint scan",
  VISITOR_CHECK_IN: "Visitor check-in",
};

const STATUS_LABELS: Record<QueueEntryStatus, string> = {
  pending: "Queued",
  in_flight: "Sending…",
  rejected: "Rejected",
  expired: "Sending window closed",
};

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

type LateResult =
  | { kind: "closed"; message: string }
  | { kind: "already_closed"; detail: string }
  | { kind: "error"; message: string };

interface EntryCardProps {
  entry: QueueEntry;
  onDismiss: (id: string) => void;
  onSubmitLate: (entry: QueueEntry) => void;
  submittingId: string | null;
  lateResult: LateResult | null;
}

function EntryCard({ entry, onDismiss, onSubmitLate, submittingId, lateResult }: EntryCardProps) {
  const isExpiredClockOut = entry.status === "expired" && entry.type === "CLOCK_OUT";
  const showDismiss = entry.status === "expired" && !isExpiredClockOut;
  const statusClass = `queue-status-badge queue-status-${entry.status}`;
  const cardClass = `queue-entry-card queue-entry-${entry.status}`;
  const isSending = submittingId === entry.id;

  return (
    <div className={cardClass}>
      <div className="queue-entry-header">
        <span className="queue-entry-type">{TYPE_LABELS[entry.type]}</span>
        <span className={statusClass}>{STATUS_LABELS[entry.status]}</span>
      </div>

      <div className="queue-entry-timestamps">
        <div className="queue-ts-row">
          <span className="queue-ts-label">At</span>
          <span className="queue-ts-value">{formatTime(entry.clientClaimedAt)}</span>
        </div>
        <div className="queue-ts-row">
          <span className="queue-ts-label">Queued</span>
          <span className="queue-ts-value">{formatTime(entry.enqueuedAt)}</span>
        </div>
      </div>

      {entry.status === "rejected" && entry.rejectedReason && (
        <p className="queue-entry-reason">{entry.rejectedReason}</p>
      )}

      {isExpiredClockOut && (
        <>
          <p className="queue-entry-reason">
            Automatic retry closed after 72 hours. Submit now to close your shift using the claimed time above.
          </p>
          {lateResult && lateResult.kind === "already_closed" && (
            <div className="queue-late-info">
              <p className="queue-late-info-text">
                Your shift is already closed — nothing more to do.
                {lateResult.detail ? ` ${lateResult.detail}` : ""}
              </p>
            </div>
          )}
          {lateResult && lateResult.kind === "closed" && (
            <div className="queue-late-info">
              <p className="queue-late-info-text">{lateResult.message}</p>
            </div>
          )}
          {lateResult && lateResult.kind === "error" && (
            <p className="queue-entry-reason" style={{ color: "var(--danger)" }}>{lateResult.message}</p>
          )}
          {!lateResult && (
            <>
              <button
                className="queue-late-submit-button"
                onClick={() => onSubmitLate(entry)}
                disabled={isSending}
              >
                {isSending ? "Submitting…" : "Submit late clock-out"}
              </button>
              <p className="queue-late-consequence">
                This closes the shift and records the time as unverified.
              </p>
            </>
          )}
        </>
      )}

      {entry.status === "expired" && !isExpiredClockOut && (
        <p className="queue-entry-reason">This action was held for more than 72 hours and was not submitted.</p>
      )}

      {showDismiss && (
        <button className="queue-dismiss-button" onClick={() => onDismiss(entry.id)}>
          Dismiss
        </button>
      )}

      {entry.attemptCount > 0 && entry.status === "pending" && (
        <p className="queue-attempt-note">{entry.attemptCount} send attempt{entry.attemptCount !== 1 ? "s" : ""} — will retry when online</p>
      )}
    </div>
  );
}

export default function QueuePage() {
  const navigate = useNavigate();
  const { auth, setOpenShift } = useAuth();
  const { entries, pendingCount, dismiss, retryNow } = useOfflineQueue();
  const [submittingId, setSubmittingId] = useState<string | null>(null);
  const [lateResults, setLateResults] = useState<Record<string, LateResult>>({});

  const isEmpty = entries.length === 0;

  async function handleSubmitLate(entry: QueueEntry) {
    if (!auth) return;
    setSubmittingId(entry.id);
    try {
      await apiFetch<ShiftResponse>("/api/v1/shifts/clock-out", {
        method: "POST",
        token: auth.token,
        body: entry.body,
        idempotencyKey: entry.id,
      });
      setOpenShift(null);
      await dismiss(entry.id);
      setLateResults((prev) => ({ ...prev, [entry.id]: { kind: "closed", message: "Shift closed." } }));
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 404 || err.status === 400) {
          // Shift was already closed (e.g. supervisor did it) — good outcome for the guard.
          setOpenShift(null);
          await dismiss(entry.id);
          setLateResults((prev) => ({
            ...prev,
            [entry.id]: { kind: "already_closed", detail: err.message },
          }));
        } else {
          setLateResults((prev) => ({
            ...prev,
            [entry.id]: { kind: "error", message: err.message },
          }));
        }
      } else {
        setLateResults((prev) => ({
          ...prev,
          [entry.id]: { kind: "error", message: "No connection — try again when connected." },
        }));
      }
    } finally {
      setSubmittingId(null);
    }
  }

  return (
    <Layout title="Outbox">
      <div className="screen-header">
        <div className="screen-header-row">
          <button className="link-button" onClick={() => navigate(-1)} style={{ fontSize: 13 }}>
            ← Back
          </button>
          {pendingCount > 0 && (
            <button className="secondary-action-inline" onClick={retryNow}>
              Retry now
            </button>
          )}
        </div>
      </div>

      <div className="screen-content">
        {isEmpty ? (
          <p className="empty">Nothing in the outbox — all actions have synced.</p>
        ) : (
          <div className="queue-list">
            {entries.map((entry) => (
              <EntryCard
                key={entry.id}
                entry={entry}
                onDismiss={dismiss}
                onSubmitLate={handleSubmitLate}
                submittingId={submittingId}
                lateResult={lateResults[entry.id] ?? null}
              />
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
}
