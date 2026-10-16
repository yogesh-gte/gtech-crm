package com.example.demo.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe rate limiting and abuse prevention service.
 * Implements sliding-window token bucket tracking with strict memory bounding
 * to prevent memory exhaustion DoS attacks.
 *
 * Architecture Note:
 * This is an in-memory, process-local rate limiter designed for standalone Spring Boot deployments.
 * For horizontally-scaled multi-instance clusters, a distributed Redis or Hazelcast store can be
 * plugged in; however, to maintain zero external infrastructure dependencies in LogSphere,
 * this bounded local implementation provides effective node-level abuse defense.
 */
@Service
public class RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);

    // Hard ceiling on tracked entries to prevent memory exhaustion
    public static final int MAX_TRACKED_ENTRIES = 10_000;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled = true;

    @Value("${app.security.rate-limit.trust-forwarded-for:false}")
    private boolean trustForwardedFor = false;

    @Value("${app.security.rate-limit.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
    private String trustedProxiesConfig = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

    private final ConcurrentHashMap<String, SlidingWindowBucket> buckets = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean isAllowed, long retryAfterSeconds, int remainingAttempts) {}

    public static class SlidingWindowBucket {
        private volatile long windowStartMs;
        private final AtomicInteger requestCount;
        private final long windowDurationMs;
        private final int maxRequests;

        public SlidingWindowBucket(int maxRequests, long windowDurationMs) {
            this.windowStartMs = System.currentTimeMillis();
            this.requestCount = new AtomicInteger(0);
            this.windowDurationMs = windowDurationMs;
            this.maxRequests = maxRequests;
        }

        public synchronized RateLimitResult tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= windowDurationMs) {
                windowStartMs = now;
                requestCount.set(0);
            }

            int current = requestCount.incrementAndGet();
            if (current <= maxRequests) {
                return new RateLimitResult(true, 0, Math.max(0, maxRequests - current));
            } else {
                long elapsed = now - windowStartMs;
                long retryAfter = Math.max(1, (windowDurationMs - elapsed + 999) / 1000);
                return new RateLimitResult(false, retryAfter, 0);
            }
        }

        public synchronized RateLimitResult peek() {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= windowDurationMs) {
                return new RateLimitResult(true, 0, maxRequests);
            }
            int current = requestCount.get();
            if (current < maxRequests) {
                return new RateLimitResult(true, 0, maxRequests - current);
            } else {
                long elapsed = now - windowStartMs;
                long retryAfter = Math.max(1, (windowDurationMs - elapsed + 999) / 1000);
                return new RateLimitResult(false, retryAfter, 0);
            }
        }

        public synchronized void reset() {
            this.requestCount.set(0);
            this.windowStartMs = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - windowStartMs) > (windowDurationMs * 2);
        }
    }

    /**
     * Resolves the client IP address safely without trusting arbitrary spoofed forwarding headers
     * unless explicitly configured behind a verified reverse proxy.
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String remoteAddr = request.getRemoteAddr();
        if (!trustForwardedFor) {
            return remoteAddr != null ? remoteAddr : "127.0.0.1";
        }

        // Only inspect X-Forwarded-For if request comes from a trusted upstream proxy
        Set<String> trustedSet = new HashSet<>(Arrays.asList(trustedProxiesConfig.split(",")));
        if (remoteAddr != null && trustedSet.contains(remoteAddr.trim())) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // First IP in list is client
                String[] parts = forwarded.split(",");
                String clientIp = parts[0].trim();
                if (!clientIp.isEmpty() && clientIp.matches("^[0-9a-fA-F:.]+$")) {
                    return clientIp;
                }
            }
        }

        return remoteAddr != null ? remoteAddr : "127.0.0.1";
    }

    /**
     * Generic rate check with automatic memory bounding.
     */
    public RateLimitResult checkLimit(String category, String key, int maxRequests, long windowSeconds) {
        if (!rateLimitEnabled) {
            return new RateLimitResult(true, 0, maxRequests);
        }

        evictIfCapacityExceeded();

        String compositeKey = category + ":" + (key != null ? key.trim().toLowerCase() : "unknown");
        long windowMs = windowSeconds * 1000;

        SlidingWindowBucket bucket = buckets.computeIfAbsent(compositeKey,
                k -> new SlidingWindowBucket(maxRequests, windowMs));

        return bucket.tryConsume();
    }

    /**
     * Inspects current login block status for an IP and account combination.
     * Enforces dual-keying: 5 failed attempts per account/IP in 15 minutes,
     * and 20 total login attempts per IP in 15 minutes to prevent IP-wide brute force.
     */
    public RateLimitResult checkLoginAllowed(String ip, String account) {
        if (!rateLimitEnabled) {
            return new RateLimitResult(true, 0, 5);
        }

        String accountKey = "login_fail_acc:" + (account != null ? account.trim().toLowerCase() : "unknown");
        SlidingWindowBucket accountBucket = buckets.get(accountKey);
        if (accountBucket != null) {
            RateLimitResult res = accountBucket.peek();
            if (!res.isAllowed()) {
                logger.warn("Login attempt blocked for account [{}] - rate limit exceeded", account);
                return res;
            }
        }

        String ipKey = "login_fail_ip:" + (ip != null ? ip.trim() : "unknown");
        SlidingWindowBucket ipBucket = buckets.get(ipKey);
        if (ipBucket != null) {
            RateLimitResult res = ipBucket.peek();
            if (!res.isAllowed()) {
                logger.warn("Login attempt blocked for IP [{}] - rate limit exceeded", ip);
                return res;
            }
        }

        return new RateLimitResult(true, 0, 5);
    }

    /**
     * Records a failed login attempt for both the account and source IP.
     */
    public void recordLoginFailure(String ip, String account) {
        if (!rateLimitEnabled) return;

        evictIfCapacityExceeded();

        long windowMs = 15 * 60 * 1000; // 15 minutes window

        if (account != null && !account.isBlank()) {
            String accountKey = "login_fail_acc:" + account.trim().toLowerCase();
            SlidingWindowBucket accountBucket = buckets.computeIfAbsent(accountKey,
                    k -> new SlidingWindowBucket(5, windowMs));
            accountBucket.tryConsume();
        }

        if (ip != null && !ip.isBlank()) {
            String ipKey = "login_fail_ip:" + ip.trim();
            SlidingWindowBucket ipBucket = buckets.computeIfAbsent(ipKey,
                    k -> new SlidingWindowBucket(20, windowMs));
            ipBucket.tryConsume();
        }
    }

    /**
     * Resets failed login counters upon successful authentication.
     */
    public void recordLoginSuccess(String ip, String account) {
        if (account != null && !account.isBlank()) {
            String accountKey = "login_fail_acc:" + account.trim().toLowerCase();
            buckets.remove(accountKey);
        }
    }

    /**
     * Password reset request rate limit: max 3 requests per 15 minutes per IP or email.
     */
    public RateLimitResult checkPasswordResetAllowed(String ip, String email) {
        if (!rateLimitEnabled) return new RateLimitResult(true, 0, 3);

        RateLimitResult ipRes = checkLimit("pwd_reset_ip", ip, 5, 15 * 60);
        if (!ipRes.isAllowed()) return ipRes;

        if (email != null && !email.isBlank()) {
            return checkLimit("pwd_reset_acc", email, 3, 15 * 60);
        }
        return ipRes;
    }

    /**
     * Registration rate limit: max 10 per hour per IP.
     */
    public RateLimitResult checkRegistrationAllowed(String ip) {
        return checkLimit("registration", ip, 10, 60 * 60);
    }

    /**
     * Heavy resource operations (Face recognition, file uploads, report exports): max 20 per minute per IP.
     */
    public RateLimitResult checkHeavyResourceAllowed(String ip, String resourceName) {
        return checkLimit("heavy:" + resourceName, ip, 20, 60);
    }

    /**
     * General baseline request rate limit: max 120 per minute per IP.
     */
    public RateLimitResult checkGeneralAllowed(String ip) {
        return checkLimit("general", ip, 120, 60);
    }

    /**
     * Enforces memory bounding by removing expired entries when bucket map reaches threshold.
     */
    private void evictIfCapacityExceeded() {
        if (buckets.size() >= MAX_TRACKED_ENTRIES) {
            buckets.entrySet().removeIf(entry -> entry.getValue().isExpired());
            // If still full after removing expired, clear a subset of oldest entries
            if (buckets.size() >= MAX_TRACKED_ENTRIES) {
                logger.warn("RateLimiterService exceeded MAX_TRACKED_ENTRIES ({}); clearing stale buckets", MAX_TRACKED_ENTRIES);
                buckets.clear();
            }
        }
    }

    /**
     * Resets all tracked buckets (used in test suites or administrative maintenance).
     */
    public void resetAll() {
        buckets.clear();
    }

    public int getTrackedKeysCount() {
        return buckets.size();
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }
}
