import { useNavigate } from "react-router-dom";
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
  expired: "Expired",
};

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function EntryCard({ entry, onDismiss }: { entry: QueueEntry; onDismiss: (id: string) => void }) {
  const showDismiss = entry.status === "expired";
  const statusClass = `queue-status-badge queue-status-${entry.status}`;
  const cardClass = `queue-entry-card queue-entry-${entry.status}`;

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

      {entry.status === "expired" && (
        <p className="queue-entry-reason">This action was held for more than 72 hours and was not submitted. Your shift remains open on the server.</p>
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
  const { entries, pendingCount, dismiss, retryNow } = useOfflineQueue();

  const isEmpty = entries.length === 0;

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
              <EntryCard key={entry.id} entry={entry} onDismiss={dismiss} />
            ))}
          </div>
        )}
      </div>
    </Layout>
  );
}
