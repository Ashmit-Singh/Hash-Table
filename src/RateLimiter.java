import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Problem 6: Distributed Rate Limiter for API Gateway
 * Demonstrates: HashMap client tracking, Token Bucket algorithm, time-based ops, thread safety
 *
 * Token Bucket: each client gets a bucket of tokens.
 *   - 1 request consumes 1 token.
 *   - Tokens refill at a steady rate (refillRate tokens/second).
 *   - Bucket holds at most maxTokens (burst capacity).
 *   - This allows burst traffic while enforcing the long-run average.
 */
public class RateLimiter {

    // ── Token Bucket ──────────────────────────────────────────────────────────

    private static class TokenBucket {
        private double tokens;                  // current token count (fractional)
        private long   lastRefillTime;          // epoch ms
        final double   maxTokens;              // burst capacity
        final double   refillRatePerMs;        // tokens added per millisecond

        TokenBucket(double maxTokens, double requestsPerHour) {
            this.maxTokens        = maxTokens;
            this.tokens           = maxTokens;  // start full
            this.lastRefillTime   = System.currentTimeMillis();
            this.refillRatePerMs  = requestsPerHour / 3_600_000.0;
        }

        /** Refill tokens based on elapsed time since last refill. */
        synchronized void refill() {
            long now = System.currentTimeMillis();
            double elapsed = now - lastRefillTime;
            tokens = Math.min(maxTokens, tokens + elapsed * refillRatePerMs);
            lastRefillTime = now;
        }

        /** Try to consume one token. Returns true if allowed. */
        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized int remainingTokens() {
            refill();
            return (int) tokens;
        }

        /** Milliseconds until next token is available. */
        synchronized long msUntilNextToken() {
            if (tokens >= 1.0) return 0;
            double deficit = 1.0 - tokens;
            return (long) Math.ceil(deficit / refillRatePerMs);
        }
    }

    // ── Rate Limiter State ────────────────────────────────────────────────────

    // clientId → TokenBucket  (O(1) lookup)
    private final ConcurrentHashMap<String, TokenBucket> clients = new ConcurrentHashMap<>();

    private final double maxTokens;          // burst limit
    private final double requestsPerHour;    // sustained rate

    // Stats
    private final AtomicLong totalAllowed = new AtomicLong(0);
    private final AtomicLong totalDenied  = new AtomicLong(0);

    public RateLimiter(double maxTokens, double requestsPerHour) {
        this.maxTokens        = maxTokens;
        this.requestsPerHour  = requestsPerHour;
    }

    // ── Core Operations ────────────────────────────────────────────────────────

    /**
     * Check rate limit for a client. O(1).
     * Creates a new bucket for unknown clients.
     */
    public RateLimitResult checkRateLimit(String clientId) {
        TokenBucket bucket = clients.computeIfAbsent(clientId,
                k -> new TokenBucket(maxTokens, requestsPerHour));

        if (bucket.tryConsume()) {
            totalAllowed.incrementAndGet();
            int remaining = bucket.remainingTokens();
            return new RateLimitResult(true, remaining,
                    "Allowed (" + remaining + " requests remaining)");
        } else {
            totalDenied.incrementAndGet();
            long retryAfterMs = bucket.msUntilNextToken();
            return new RateLimitResult(false, 0,
                    "Denied (0 requests remaining, retry after " + retryAfterMs + "ms)");
        }
    }

    /** Get rate limit status for a client without consuming a token. */
    public RateLimitStatus getRateLimitStatus(String clientId) {
        TokenBucket bucket = clients.get(clientId);
        if (bucket == null) return new RateLimitStatus(clientId, (int) maxTokens, (int) maxTokens, 0);
        int remaining = bucket.remainingTokens();
        long resetMs = bucket.msUntilNextToken();
        return new RateLimitStatus(clientId, remaining, (int) maxTokens, resetMs);
    }

    public void printStats() {
        long total = totalAllowed.get() + totalDenied.get();
        System.out.printf("%nRate Limiter Stats:%n");
        System.out.printf("  Total requests : %,d%n", total);
        System.out.printf("  Allowed        : %,d (%.1f%%)%n", totalAllowed.get(),
                total == 0 ? 0 : 100.0 * totalAllowed.get() / total);
        System.out.printf("  Denied         : %,d (%.1f%%)%n", totalDenied.get(),
                total == 0 ? 0 : 100.0 * totalDenied.get() / total);
        System.out.printf("  Active clients : %d%n", clients.size());
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record RateLimitResult(boolean allowed, int remaining, String message) {}
    public record RateLimitStatus(String clientId, int remaining, int limit, long msUntilReset) {
        @Override public String toString() {
            return String.format("{clientId: \"%s\", remaining: %d, limit: %d, resetInMs: %d}",
                    clientId, remaining, limit, msUntilReset);
        }
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        // 1000 requests/hour, burst of 10
        RateLimiter limiter = new RateLimiter(10, 1000);

        System.out.println("=== Distributed Rate Limiter ===\n");
        System.out.println("Config: 1000 req/hour, burst capacity: 10\n");

        // Client abc123 fires 12 rapid requests (should exhaust burst quickly)
        System.out.println("-- Client abc123: 12 rapid requests --");
        for (int i = 1; i <= 12; i++) {
            RateLimitResult result = limiter.checkRateLimit("abc123");
            System.out.printf("  Request %2d: %s%n", i, result.message());
        }

        // Different client is unaffected
        System.out.println("\n-- Client xyz789: independent bucket --");
        RateLimitResult r = limiter.checkRateLimit("xyz789");
        System.out.println("  Request 1: " + r.message());

        // Status check
        System.out.println("\n-- Status check for abc123 --");
        System.out.println("  " + limiter.getRateLimitStatus("abc123"));

        // Concurrent simulation: 100,000 clients, each sending 5 requests
        System.out.println("\n-- Concurrent load: 100,000 clients × 5 requests --");
        ExecutorService pool = Executors.newFixedThreadPool(500);
        CountDownLatch latch = new CountDownLatch(100_000);
        for (int i = 0; i < 100_000; i++) {
            String clientId = "client_" + i;
            pool.submit(() -> {
                for (int j = 0; j < 5; j++) limiter.checkRateLimit(clientId);
                latch.countDown();
            });
        }
        latch.await();
        pool.shutdown();

        limiter.printStats();
    }
}
