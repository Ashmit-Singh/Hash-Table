import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Problem 3: DNS Cache with TTL (Time To Live)
 * Demonstrates: Custom Entry class, chaining collision resolution, TTL expiry, LRU eviction, hit/miss stats
 */
public class DNSCache {

    // ── Entry ─────────────────────────────────────────────────────────────────

    private static class DNSEntry {
        final String domain;
        final String ipAddress;
        final long createdAt;       // epoch ms
        final long expiresAt;       // epoch ms
        volatile long lastAccessed; // for LRU

        DNSEntry(String domain, String ip, long ttlSeconds) {
            this.domain = domain;
            this.ipAddress = ip;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = createdAt + ttlSeconds * 1000;
            this.lastAccessed = createdAt;
        }

        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }

        long remainingTtl() {
            long rem = (expiresAt - System.currentTimeMillis()) / 1000;
            return Math.max(0, rem);
        }
    }

    // ── Cache State ───────────────────────────────────────────────────────────

    private final int maxCapacity;
    // LinkedHashMap in access-order acts as LRU
    private final LinkedHashMap<String, DNSEntry> cache;
    private final AtomicLong hits   = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong totalLookupNs = new AtomicLong(0);
    private final AtomicLong lookupCount    = new AtomicLong(0);

    // Background cleaner thread
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();

    public DNSCache(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        // access-order=true → LRU semantics; removeEldestEntry handles eviction
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, DNSEntry> eldest) {
                return size() > maxCapacity;
            }
        };
        // Clean expired entries every 60 seconds
        cleaner.scheduleAtFixedRate(this::evictExpired, 60, 60, TimeUnit.SECONDS);
    }

    // ── Core Operations ────────────────────────────────────────────────────────

    /**
     * Resolve a domain name.
     * Returns IP from cache if valid, otherwise queries "upstream" (simulated).
     */
    public String resolve(String domain) {
        long start = System.nanoTime();

        synchronized (cache) {
            DNSEntry entry = cache.get(domain);

            if (entry != null && !entry.isExpired()) {
                // Cache HIT
                entry.lastAccessed = System.currentTimeMillis();
                hits.incrementAndGet();
                recordTiming(System.nanoTime() - start);
                System.out.printf("resolve(\"%s\") → Cache HIT  → %s (TTL remaining: %ds, %.2fms)%n",
                        domain, entry.ipAddress, entry.remainingTtl(),
                        (System.nanoTime() - start) / 1_000_000.0);
                return entry.ipAddress;
            }

            // Cache MISS or EXPIRED
            String status = (entry != null) ? "Cache EXPIRED" : "Cache MISS";
            misses.incrementAndGet();

            // Simulate upstream DNS query (normally 50-150ms; we fake it fast)
            String ip = queryUpstream(domain);
            long defaultTtl = 300; // 5 minutes
            DNSEntry newEntry = new DNSEntry(domain, ip, defaultTtl);
            cache.put(domain, newEntry);

            recordTiming(System.nanoTime() - start);
            System.out.printf("resolve(\"%s\") → %s → Query upstream → %s (TTL: %ds, %.2fms)%n",
                    domain, status, ip, defaultTtl,
                    (System.nanoTime() - start) / 1_000_000.0);
            return ip;
        }
    }

    /** Manually insert a known DNS mapping (e.g., from a zone file). */
    public void insert(String domain, String ip, long ttlSeconds) {
        synchronized (cache) {
            cache.put(domain, new DNSEntry(domain, ip, ttlSeconds));
        }
    }

    /** Invalidate a cache entry (e.g., IP changed). */
    public void invalidate(String domain) {
        synchronized (cache) { cache.remove(domain); }
        System.out.println("Invalidated: " + domain);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public void getCacheStats() {
        long total = hits.get() + misses.get();
        double hitRate  = total == 0 ? 0 : 100.0 * hits.get() / total;
        double avgMs    = lookupCount.get() == 0 ? 0
                : totalLookupNs.get() / 1_000_000.0 / lookupCount.get();
        int size;
        synchronized (cache) { size = cache.size(); }

        System.out.printf("%ngetCacheStats() →%n");
        System.out.printf("  Hit Rate     : %.1f%%%n", hitRate);
        System.out.printf("  Hits / Misses: %d / %d%n", hits.get(), misses.get());
        System.out.printf("  Avg Lookup   : %.3f ms%n", avgMs);
        System.out.printf("  Cache size   : %d / %d%n", size, maxCapacity);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Simulate an upstream DNS resolver. */
    private String queryUpstream(String domain) {
        // Real impl would send a UDP packet; we fake deterministic IPs
        return "93." + (Math.abs(domain.hashCode()) % 255) + ".168.1";
    }

    private void evictExpired() {
        synchronized (cache) {
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
    }

    private void recordTiming(long nanos) {
        totalLookupNs.addAndGet(nanos);
        lookupCount.incrementAndGet();
    }

    public void shutdown() { cleaner.shutdown(); }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        DNSCache dns = new DNSCache(1000);

        System.out.println("=== DNS Cache with TTL ===\n");

        dns.resolve("google.com");      // MISS → query upstream
        dns.resolve("google.com");      // HIT
        dns.resolve("github.com");      // MISS
        dns.resolve("github.com");      // HIT
        dns.resolve("openai.com");      // MISS
        dns.resolve("google.com");      // HIT

        // Simulate an expired entry by inserting with 0 TTL
        dns.insert("expired.com", "1.2.3.4", 0);
        Thread.sleep(10); // ensure it expires
        dns.resolve("expired.com");     // EXPIRED → query upstream

        dns.invalidate("github.com");
        dns.resolve("github.com");      // MISS after invalidation

        dns.getCacheStats();
        dns.shutdown();
    }
}
