export type QueueEntryType = "CLOCK_OUT" | "CHECKPOINT_SCAN" | "VISITOR_CHECK_IN";
export type QueueEntryStatus = "pending" | "in_flight" | "rejected" | "expired";

export interface QueueEntry {
  id: string;
  type: QueueEntryType;
  endpoint: string;
  path: string;
  body: Record<string, unknown>;
  clientClaimedAt: string;
  enqueuedAt: string;
  status: QueueEntryStatus;
  rejectedReason?: string;
  attemptCount: number;
}

const DB_NAME = "guard-psp-queue";
const STORE = "queue";
const TTL_MS = 72 * 60 * 60 * 1000;

function openDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: "id" });
        store.createIndex("status", "status");
        store.createIndex("enqueuedAt", "enqueuedAt");
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function idbPut(db: IDBDatabase, entry: QueueEntry): Promise<void> {
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, "readwrite");
    t.oncomplete = () => resolve();
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error ?? new Error("IDB transaction aborted"));
    t.objectStore(STORE).put(entry);
  });
}

function idbDelete(db: IDBDatabase, id: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, "readwrite");
    t.oncomplete = () => resolve();
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error ?? new Error("IDB transaction aborted"));
    t.objectStore(STORE).delete(id);
  });
}

function idbGetAll(db: IDBDatabase): Promise<QueueEntry[]> {
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, "readonly");
    const req = t.objectStore(STORE).getAll();
    req.onsuccess = () => resolve(req.result as QueueEntry[]);
    req.onerror = () => reject(req.error);
  });
}

let dbPromise: Promise<IDBDatabase> | null = null;
function getDB(): Promise<IDBDatabase> {
  if (!dbPromise) dbPromise = openDB();
  return dbPromise;
}

export async function enqueue(
  entry: Omit<QueueEntry, "enqueuedAt" | "status" | "attemptCount"> & { id?: string },
): Promise<void> {
  const db = await getDB();
  const full: QueueEntry = {
    ...entry,
    id: entry.id ?? crypto.randomUUID(),
    enqueuedAt: new Date().toISOString(),
    status: "pending",
    attemptCount: 0,
  };
  await idbPut(db, full);
}

export async function getAllEntries(): Promise<QueueEntry[]> {
  const db = await getDB();
  const entries = await idbGetAll(db);
  return entries.sort((a, b) => a.enqueuedAt.localeCompare(b.enqueuedAt));
}

export async function updateEntry(entry: QueueEntry): Promise<void> {
  const db = await getDB();
  await idbPut(db, entry);
}

export async function removeEntry(id: string): Promise<void> {
  const db = await getDB();
  await idbDelete(db, id);
}

export async function resetInFlight(): Promise<void> {
  const db = await getDB();
  const entries = await idbGetAll(db);
  await Promise.all(
    entries
      .filter((e) => e.status === "in_flight")
      .map((e) => idbPut(db, { ...e, status: "pending" })),
  );
}

export async function expireOldEntries(): Promise<void> {
  const db = await getDB();
  const entries = await idbGetAll(db);
  const cutoff = Date.now() - TTL_MS;
  await Promise.all(
    entries
      .filter((e) => e.status === "pending" && new Date(e.enqueuedAt).getTime() < cutoff)
      .map((e) => idbPut(db, { ...e, status: "expired" })),
  );
}

const TYPE_FLUSH_ORDER: QueueEntryType[] = ["VISITOR_CHECK_IN", "CHECKPOINT_SCAN", "CLOCK_OUT"];

export function sortForFlush(entries: QueueEntry[]): QueueEntry[] {
  return entries
    .filter((e) => e.status === "pending")
    .sort((a, b) => {
      const ai = TYPE_FLUSH_ORDER.indexOf(a.type);
      const bi = TYPE_FLUSH_ORDER.indexOf(b.type);
      if (ai !== bi) return ai - bi;
      return a.enqueuedAt.localeCompare(b.enqueuedAt);
    });
}
