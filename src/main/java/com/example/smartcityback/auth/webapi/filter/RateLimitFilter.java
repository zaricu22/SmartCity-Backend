package com.example.smartcityback.auth.webapi.filter;

/*
 * RATE LIMITING — Design decisions
 *
 * Applied to: POST /v1/auth/login, /v1/auth/refresh, and /v1/auth/register.
 *   - Login:    primary brute-force vector (password guessing per IP).
 *   - Refresh:  token rotation abuse.
 *   - Register: without this, an attacker can enumerate whether emails exist (via 409)
 *               or exhaust CPU with BCrypt hashing at scale.
 *   - Logout requires a valid JWT — already guarded by authentication.
 *
 * Per-IP bucket: each client IP gets its own Bucket4j token bucket.
 *   - X-Forwarded-For respected for requests behind a reverse proxy
 *     (only the first — leftmost — IP is trusted).
 *   - ConcurrentHashMap + Bucket4j are both thread-safe.
 *
 * Bucket strategy: classic token bucket.
 *   - Capacity N, refills N tokens per minute (greedy — continuous, not bursty).
 *   - Default: 10 attempts per minute per IP (configurable via properties).
 *   - tryConsume(1): non-blocking; returns false immediately when empty → 429.
 *
 * No eviction of idle IP buckets: acceptable at dev/educational scale.
 *   Production: use bucket4j-redis for a shared, distributed rate limit store
 *   that survives app restarts and scales across multiple instances.
 *
 * Double-registration guard: OncePerRequestFilter ensures the filter
 *   executes exactly once per request even if Spring Boot auto-registers it
 *   as a servlet filter AND SecurityConfig adds it to the security chain.
 */

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_PATHS = Set.of(
            "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/register"
    );

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int refillPerMinute;

    public RateLimitFilter(
            @Value("${app.rate-limit.auth.capacity:10}") int capacity,
            @Value("${app.rate-limit.auth.refill-per-minute:10}") int refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isRateLimited(request)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":" + HttpStatus.TOO_MANY_REQUESTS.value() + ",\"error\":\"Too Many Requests\"}");
        }
    }

    private boolean isRateLimited(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && RATE_LIMITED_PATHS.stream().anyMatch(request.getRequestURI()::endsWith);
    }

    // X-Forwarded-For may contain a comma-separated chain — take the leftmost (original client)
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
