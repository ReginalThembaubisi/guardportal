import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { apiFetch, ApiError } from "./api/client";
import {
  type QueueEntry,
  type QueueEntryType,
  enqueue as idbEnqueue,
  expireOldEntries,
  getAllEntries,
  removeEntry,
  resetInFlight,
  sortForFlush,
  updateEntry,
} from "./offlineQueue";
import { useAuth } from "./auth/AuthContext";

interface OfflineQueueContextValue {
  entries: QueueEntry[];
  pendingCount: number;
  pendingClockOut: QueueEntry | null;
  rejectedClockOut: QueueEntry | null;
  enqueueAction: (entry: {
    type: QueueEntryType;
    path: string;
    body: Record<string, unknown>;
    clientClaimedAt: string;
  }) => Promise<void>;
  dismiss: (id: string) => Promise<void>;
  retryNow: () => void;
}

const OfflineQueueContext = createContext<OfflineQueueContextValue | undefined>(undefined);

export function OfflineQueueProvider({ children }: { children: ReactNode }) {
  const { auth } = useAuth();
  const [entries, setEntries] = useState<QueueEntry[]>([]);
  const flushingRef = useRef(false);

  const reload = useCallback(async () => {
    try {
      const all = await getAllEntries();
      setEntries(all);
    } catch {
      // Non-fatal — IDB unavailable in some private-window configs.
    }
  }, []);

  useEffect(() => {
    resetInFlight().then(reload);
  }, [reload]);

  const flush = useCallback(async () => {
    if (!auth || flushingRef.current) return;
    flushingRef.current = true;
    try {
      await expireOldEntries();
      const all = await getAllEntries();
      const toFlush = sortForFlush(all);
      for (const entry of toFlush) {
        const inFlight = { ...entry, status: "in_flight" as const, attemptCount: entry.attemptCount + 1 };
        await updateEntry(inFlight);
        try {
          await apiFetch(entry.path, {
            method: "POST",
            token: auth.token,
            body: entry.body,
            idempotencyKey: entry.id,
          });
          await removeEntry(entry.id);
        } catch (err) {
          if (err instanceof ApiError) {
            if (err.status === 400 || err.status === 404 || err.status === 422 || err.status === 403) {
              await updateEntry({
                ...inFlight,
                status: "rejected",
                rejectedReason: err.message,
              });
            } else {
              // 401/408/409/429/5xx — transient, retry later
              await updateEntry({ ...inFlight, status: "pending" });
            }
          } else {
            // Network error — leave as pending
            await updateEntry({ ...inFlight, status: "pending" });
          }
        }
      }
    } finally {
      flushingRef.current = false;
      reload();
    }
  }, [auth, reload]);

  const retryNow = useCallback(() => {
    flush();
  }, [flush]);

  useEffect(() => {
    const handleOnline = () => flush();
    window.addEventListener("online", handleOnline);
    if (navigator.onLine) flush();
    return () => window.removeEventListener("online", handleOnline);
  }, [flush]);

  const enqueueAction = useCallback(
    async (entry: {
      type: QueueEntryType;
      path: string;
      body: Record<string, unknown>;
      clientClaimedAt: string;
    }) => {
      // Let QuotaExceededError and other IDB errors propagate — callers must handle them.
      await idbEnqueue({
        type: entry.type,
        endpoint: `POST ${entry.path}`,
        path: entry.path,
        body: entry.body,
        clientClaimedAt: entry.clientClaimedAt,
      });
      await reload();
    },
    [reload],
  );

  const dismiss = useCallback(
    async (id: string) => {
      await removeEntry(id);
      await reload();
    },
    [reload],
  );

  const pendingCount = entries.filter((e) => e.status === "pending" || e.status === "in_flight").length;
  const pendingClockOut = entries.find((e) => e.type === "CLOCK_OUT" && e.status === "pending") ?? null;
  const rejectedClockOut = entries.find((e) => e.type === "CLOCK_OUT" && e.status === "rejected") ?? null;

  const value = useMemo<OfflineQueueContextValue>(
    () => ({ entries, pendingCount, pendingClockOut, rejectedClockOut, enqueueAction, dismiss, retryNow }),
    [entries, pendingCount, pendingClockOut, rejectedClockOut, enqueueAction, dismiss, retryNow],
  );

  return <OfflineQueueContext.Provider value={value}>{children}</OfflineQueueContext.Provider>;
}

export function useOfflineQueue(): OfflineQueueContextValue {
  const ctx = useContext(OfflineQueueContext);
  if (!ctx) throw new Error("useOfflineQueue must be used within OfflineQueueProvider");
  return ctx;
}
