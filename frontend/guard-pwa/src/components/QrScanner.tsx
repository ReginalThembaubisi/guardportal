import { Html5Qrcode } from "html5-qrcode";
import { useEffect, useId, useRef, useState } from "react";

/**
 * Camera-based QR scanner. Decodes continuously, then pauses itself after
 * each successful decode (calling onDecode) so the same code can't be
 * submitted twice in a row — the parent re-enables scanning by remounting
 * or the guard taps "Scan next". Manual text entry is always available
 * alongside this as a fallback, never gated behind it — camera failures
 * (no permission, no camera, unsupported browser) just leave this
 * component showing an error instead of blocking the rest of the page.
 */
export default function QrScanner({ onDecode }: { onDecode: (decodedText: string) => void }) {
  const elementId = useId().replace(/:/g, "-");
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paused, setPaused] = useState(false);
  const [starting, setStarting] = useState(true);

  useEffect(() => {
    const scanner = new Html5Qrcode(elementId, { verbose: false });
    scannerRef.current = scanner;
    let cancelled = false;

    const startPromise = scanner
      .start(
        { facingMode: "environment" },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (decodedText) => {
          if (cancelled) return;
          scanner.pause(true);
          setPaused(true);
          onDecode(decodedText);
        },
        () => {
          // Per-frame "no code found" — expected on almost every frame, not an error.
        },
      )
      .then(() => {
        if (!cancelled) setStarting(false);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setStarting(false);
        setError(
          err instanceof Error
            ? `Camera unavailable: ${err.message}`
            : "Camera unavailable — allow camera access or use manual entry below.",
        );
      });

    return () => {
      cancelled = true;
      // start() is async (it awaits camera permission), so React StrictMode's
      // dev-only mount→unmount→remount can run this cleanup before the camera
      // has actually attached. Chaining onto startPromise guarantees whatever
      // did start gets torn down, however long it took to get there — a plain
      // isScanning check here would miss anything still starting.
      //
      // Deliberately calling only stop(), never clear(): stop() surgically
      // removes just *this* instance's own video/canvas/shaded-region nodes
      // (see html5-qrcode's RenderedCameraImpl.close, which removeChild()s
      // only its own surface element). clear() instead does
      // element.innerHTML = "" on the shared container — if the StrictMode
      // remount's own scanner has already attached its video by the time
      // this runs, clear() would wipe that live feed out too, not just the
      // stale one this instance owns.
      startPromise.finally(() => {
        if (scanner.isScanning) {
          scanner.stop().catch(() => {});
        }
      });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elementId]);

  function scanNext() {
    scannerRef.current?.resume();
    setPaused(false);
  }

  return (
    <div className="qr-scanner">
      <div id={elementId} className="qr-scanner-view" />
      {starting && !error && <p className="dev-hint">Starting camera…</p>}
      {error && <p className="error">{error}</p>}
      {paused && (
        <button type="button" className="qr-scan-next-button" onClick={scanNext}>
          Scan next
        </button>
      )}
    </div>
  );
}
