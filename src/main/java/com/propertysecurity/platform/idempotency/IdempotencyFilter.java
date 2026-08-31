package com.propertysecurity.platform.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Idempotency filter for offline-queue writes.
 *
 * Every write buffered by guard-pwa carries an Idempotency-Key header (a
 * client-generated UUID). This filter:
 *
 *   - Skips non-POST requests and requests without the header.
 *   - Inserts an in-flight row before the chain runs; if the INSERT wins the
 *     unique constraint race, this thread owns processing.
 *   - On duplicate key: reads the existing row and decides:
 *       403 — principal mismatch (cross-guard key reuse)
 *       422 — key used for a different endpoint
 *       409 — key genuinely in-flight (fresh, < 120 s)
 *       replay — key completed; returns the cached response
 *   - Stale in-flight (≥ 120 s, JVM crash path): one thread reclaims via a
 *     conditional UPDATE and re-processes; others 409.
 *   - Caches only 2xx + domain 4xx (400, 404, 409, 422). Passes through
 *     401, 403, 408, 429, 5xx uncached so the client retries.
 *   - The finally block ALWAYS finalizes or deletes the row, so a crashed
 *     JVM never strands a key in in_flight=true permanently.
 *
 * Runs after JwtAuthenticationFilter so the principal is already in the
 * SecurityContext. Registered via SecurityConfig.addFilterAfter() only;
 * FilterRegistrationBean.setEnabled(false) prevents the servlet container
 * from also registering it (double-run).
 */
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String idemKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);

        if (idemKey == null || idemKey.isBlank() || !"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // After JwtAuthenticationFilter — principal is set iff the JWT was valid.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long principalId)) {
            // Not authenticated yet; Spring Security will reject at the authorization layer.
            chain.doFilter(request, response);
            return;
        }

        String endpoint = request.getMethod() + " " + request.getRequestURI();

        boolean ownedByUs = false;
        try {
            idempotencyService.insert(idemKey, endpoint, principalId);
            ownedByUs = true;
        } catch (DataIntegrityViolationException conflict) {
            IdempotencyKey existing = idempotencyService.findByKey(idemKey).orElse(null);

            if (existing == null) {
                // Row was deleted between the INSERT failure and this read (very unlikely).
                // Treat as a transient failure and let the client retry.
                writeJson(response, HttpServletResponse.SC_CONFLICT, "Concurrent idempotency conflict — retry");
                return;
            }

            if (!existing.getPrincipalId().equals(principalId)) {
                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "Idempotency key belongs to a different principal");
                return;
            }

            if (!existing.getEndpoint().equals(endpoint)) {
                writeJson(response, 422,
                        "Idempotency key was used for a different endpoint: " + existing.getEndpoint());
                return;
            }

            if (existing.isInFlight()) {
                boolean reclaimed = idempotencyService.tryReclaim(idemKey);
                if (reclaimed) {
                    ownedByUs = true;
                } else {
                    writeJson(response, HttpServletResponse.SC_CONFLICT,
                            "Request is still in flight — retry after the first attempt completes");
                    return;
                }
            } else {
                // Completed — replay the cached response.
                replayResponse(response, existing);
                return;
            }
        }

        // This thread owns the key. Capture the response body so we can persist it.
        CapturingResponseWrapper capturing = new CapturingResponseWrapper(response);
        try {
            chain.doFilter(request, capturing);
        } finally {
            int status = response.getStatus();
            byte[] body = capturing.getCapturedBody();
            try {
                if (shouldCache(status)) {
                    idempotencyService.finalize(idemKey, status, new String(body, StandardCharsets.UTF_8));
                } else {
                    idempotencyService.delete(idemKey);
                }
            } catch (Exception ex) {
                // Finalization failure must not swallow the real response. Best-effort delete.
                try {
                    idempotencyService.delete(idemKey);
                } catch (Exception ignored) {
                }
            }
            capturing.copyBodyToResponse();
        }
    }

    private static boolean shouldCache(int status) {
        return (status >= 200 && status < 300)
                || status == 400 || status == 404 || status == 409 || status == 422;
    }

    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String body = "{\"status\":" + status + ",\"message\":\"" + escapeJson(message) + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private static void replayResponse(HttpServletResponse response, IdempotencyKey key) throws IOException {
        response.setStatus(key.getStatusCode());
        response.setContentType("application/json;charset=UTF-8");
        byte[] body = key.getResponseBody() != null
                ? key.getResponseBody().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Captures body writes without passing them through to the underlying
     * response. This lets the filter inspect and persist the body before
     * writing it to the actual output stream (copyBodyToResponse). Without
     * this, ContentCachingResponseWrapper would write bytes to the client
     * during chain execution, making true replay impossible.
     */
    private static class CapturingResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(2048);
        private PrintWriter printWriter;
        private ServletOutputStream outputStream;

        CapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new DelegatingServletOutputStream(buffer);
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            if (printWriter == null) {
                printWriter = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);
            }
            return printWriter;
        }

        byte[] getCapturedBody() {
            if (printWriter != null) {
                printWriter.flush();
            }
            return buffer.toByteArray();
        }

        void copyBodyToResponse() throws IOException {
            if (printWriter != null) {
                printWriter.flush();
            }
            byte[] body = buffer.toByteArray();
            HttpServletResponse underlying = (HttpServletResponse) getResponse();
            underlying.setContentLength(body.length);
            underlying.getOutputStream().write(body);
            underlying.getOutputStream().flush();
        }
    }

    private static class DelegatingServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream target;

        DelegatingServletOutputStream(ByteArrayOutputStream target) {
            this.target = target;
        }

        @Override
        public void write(int b) {
            target.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            target.write(b, off, len);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // Synchronous path only — the guard-pwa queue sends standard blocking requests.
        }
    }
}
