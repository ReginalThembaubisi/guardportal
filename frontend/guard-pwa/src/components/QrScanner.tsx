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
        return true;
      })
      .catch((err: unknown) => {
        if (cancelled) return false;
        setStarting(false);
        setError(
          err instanceof Error
            ? `Camera unavailable: ${err.message}`
            : "Camera unavailable — allow camera access or use manual entry below.",
        );
        return false;
      });

    return () => {
      cancelled = true;
      // start() resolving does NOT mean the camera is actually live yet:
      // html5-qrcode's isScanning flag only flips true once the <video>
      // element's native "playing" event fires (see RenderedCameraImpl.
      // setupSurface in the library source), which happens strictly later
      // than start()'s own promise resolution. Checking isScanning right
      // when startPromise settles was reliably too early — it read false,
      // so stop() never ran, and a StrictMode-losing instance's camera (and
      // its <video>) just kept running forever alongside the surviving
      // one's. Poll for the real transition instead of assuming it already
      // happened; give up after a few seconds if it never does (start()
      // failed, or the camera genuinely never became scanning).
      startPromise.then((started) => {
        if (!started) return;
        let attemptsLeft = 50;
        const tryStop = () => {
          if (scanner.isScanning) {
            // Surgical: removes only this instance's own video/canvas/
            // shaded-region nodes (RenderedCameraImpl.close only
            // removeChild()s its own surface) — safe even if a sibling
            // instance is still using the same container. Deliberately
            // never calling clear(), which does element.innerHTML = ""
            // on the whole shared container and would wipe a sibling's
            // live video out too.
            scanner.stop().catch(() => {});
          } else if (attemptsLeft > 0) {
            attemptsLeft -= 1;
            setTimeout(tryStop, 100);
          }
        };
        tryStop();
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
