import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Problem 10: Multi-Level Cache System with Hash Tables
 * Demonstrates: Multiple HashMaps, LRU via LinkedHashMap, promotion/demotion,
 *               cache invalidation, per-level hit/miss statistics
 *
 * Architecture:
 *   L1 (in-memory HashMap, capacity 10K, fastest)
 *   L2 (SSD-backed simulation, capacity 100K, medium)
 *   L3 (database simulation, unlimited, slowest)
 */
public class MultiLevelCache {

    // ── Video Data ────────────────────────────────────────────────────────────

    public record VideoData(String videoId, String title, byte[] content) {}

    // ── LRU Cache Layer ────────────────────────────────────────────────────────

    /**
     * A thread-safe LRU cache backed by a LinkedHashMap (access-order).
     * LinkedHashMap.removeEldestEntry() handles eviction automatically.
     */
    private static class LRUCacheLayer {
        private final String name;
        private final int capacity;
        private final long simulatedLatencyMs;

        private final LinkedHashMap<String, VideoData> store;
        private final AtomicLong hits   = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);

        LRUCacheLayer(String name, int capacity, long simulatedLatencyMs) {
            this.name = name;
            this.capacity = capacity;
            this.simulatedLatencyMs = simulatedLatencyMs;
            this.store = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, VideoData> eldest) {
                    return size() > capacity;
                }
            };
        }

        synchronized VideoData get(String videoId) {
            totalLatencyMs.addAndGet(simulatedLatencyMs);
            VideoData data = store.get(videoId);
            if (data != null) { hits.incrementAndGet(); return data; }
            misses.incrementAndGet();
            return null;
        }

        synchronized void put(String videoId, VideoData data) {
            store.put(videoId, data);
        }

        synchronized void invalidate(String videoId) {
            store.remove(videoId);
        }

        synchronized void invalidateAll() { store.clear(); }

        synchronized boolean contains(String videoId) { return store.containsKey(videoId); }

        synchronized int size() { return store.size(); }

        double hitRate() {
            long total = hits.get() + misses.get();
            return total == 0 ? 0 : 100.0 * hits.get() / total;
        }

        double avgLatencyMs() {
            long total = hits.get() + misses.get();
            return total == 0 ? 0 : (double) totalLatencyMs.get() / total;
        }

        void printStats() {
            System.out.printf("  %-4s: Hit Rate %5.1f%%, Avg Time: %.1fms, Size: %d/%d%n",
                    name, hitRate(), avgLatencyMs(), size(), capacity);
        }

        long totalRequests() { return hits.get() + misses.get(); }
        long totalHits()     { return hits.get(); }
    }

    // ── Simulated Database (L3) ────────────────────────────────────────────────

    private static class Database {
        private final HashMap<String, VideoData> store = new HashMap<>();
        private final AtomicLong hits   = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        static final long LATENCY_MS = 150;

        void seed(String videoId, VideoData data) { store.put(videoId, data); }

        VideoData get(String videoId) {
            VideoData data = store.get(videoId);
            if (data != null) { hits.incrementAndGet(); return data; }
            misses.incrementAndGet();
            return null;
        }

        void put(String videoId, VideoData data) { store.put(videoId, data); }

        double hitRate() {
            long total = hits.get() + misses.get();
            return total == 0 ? 0 : 100.0 * hits.get() / total;
        }

        void printStats() {
            System.out.printf("  %-4s: Hit Rate %5.1f%%, Avg Time: %.0fms%n",
                    "L3", hitRate(), (double) LATENCY_MS);
        }

        long totalHits() { return hits.get(); }
        long totalRequests() { return hits.get() + misses.get(); }
    }

    // ── Multi-Level Cache ──────────────────────────────────────────────────────

    private final LRUCacheLayer l1 = new LRUCacheLayer("L1", 10_000, 0);
    private final LRUCacheLayer l2 = new LRUCacheLayer("L2", 100_000, 5);
    private final Database      l3 = new Database();

    // Access count for L2→L1 promotion decision
    private final ConcurrentHashMap<String, AtomicInteger> accessCount = new ConcurrentHashMap<>();
    private static final int PROMOTION_THRESHOLD = 3; // promote after 3 L2 hits

    // ── Core Operations ────────────────────────────────────────────────────────

    /**
     * Retrieve video: check L1 → L2 → L3.
     * Promotes videos up the hierarchy based on access patterns.
     */
    public VideoData getVideo(String videoId) {
        long start = System.currentTimeMillis();

        // L1 check
        VideoData data = l1.get(videoId);
        if (data != null) {
            System.out.printf("getVideo(\"%s\") → L1 HIT  (%.0fms)%n", videoId, l1.simulatedLatencyMs * 1.0);
            return data;
        }
        System.out.printf("getVideo(\"%s\") → L1 MISS%n", videoId);

        // L2 check
        data = l2.get(videoId);
        if (data != null) {
            System.out.printf("  → L2 HIT  (%dms)%n", l2.simulatedLatencyMs);
            maybePromoteToL1(videoId, data);
            return data;
        }
        System.out.printf("  → L2 MISS%n");

        // L3 (database)
        data = l3.get(videoId);
        if (data != null) {
            System.out.printf("  → L3 HIT  (%dms)%n", Database.LATENCY_MS);
            l2.put(videoId, data); // add to L2
            accessCount.computeIfAbsent(videoId, k -> new AtomicInteger(0)).set(1);
            System.out.printf("  → Added to L2 (access count: 1)%n");
        } else {
            System.out.printf("  → L3 MISS (video not found)%n");
        }

        long elapsed = System.currentTimeMillis() - start;
        if (data != null) System.out.printf("  → Total: %dms%n", Math.max(elapsed, Database.LATENCY_MS));
        return data;
    }

    private void maybePromoteToL1(String videoId, VideoData data) {
        int count = accessCount.computeIfAbsent(videoId, k -> new AtomicInteger(0))
                .incrementAndGet();
        if (count >= PROMOTION_THRESHOLD) {
            l1.put(videoId, data);
            System.out.printf("  → Promoted to L1 (access count: %d)%n", count);
        } else {
            System.out.printf("  → Access count: %d (threshold: %d)%n", count, PROMOTION_THRESHOLD);
        }
    }

    /**
     * Invalidate a video across all cache levels (e.g., content updated).
     */
    public void invalidate(String videoId) {
        l1.invalidate(videoId);
        l2.invalidate(videoId);
        accessCount.remove(videoId);
        System.out.println("Invalidated \"" + videoId + "\" across L1, L2");
    }

    /** Seed the database with test videos. */
    public void seedDatabase(String videoId, String title) {
        l3.seed(videoId, new VideoData(videoId, title, new byte[0]));
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    public void getStatistics() {
        long totalReqs = l1.totalRequests() + l2.totalRequests() + l3.totalRequests() -
                l2.totalRequests() - l3.totalRequests(); // only count top-level L1 requests

        // Recalculate properly
        long l1Reqs = l1.totalRequests();
        long overallHits = l1.totalHits() + l2.totalHits() + l3.totalHits();
        long overallReqs = l1Reqs; // each user request goes through L1 first

        System.out.println("\ngetStatistics() →");
        l1.printStats();
        l2.printStats();
        l3.printStats();
        System.out.printf("  Overall: Hit Rate %.1f%%, Size(L1):%d, Size(L2):%d%n",
                overallReqs == 0 ? 0 : 100.0 * overallHits / overallReqs,
                l1.size(), l2.size());
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        MultiLevelCache cache = new MultiLevelCache();

        System.out.println("=== Multi-Level Cache System (L1/L2/L3) ===\n");

        // Seed database
        for (int i = 1; i <= 200; i++) cache.seedDatabase("video_" + i, "Title " + i);

        System.out.println("--- First access (L3 fallback) ---");
        cache.getVideo("video_123");

        System.out.println("\n--- Second access (L2 hit) ---");
        cache.getVideo("video_123");

        System.out.println("\n--- Third access (L2 hit, promotes to L1) ---");
        cache.getVideo("video_123");

        System.out.println("\n--- Fourth access (L1 hit) ---");
        cache.getVideo("video_123");

        System.out.println("\n--- Different video (full L3 path) ---");
        cache.getVideo("video_999");

        System.out.println("\n--- Simulate 10,000 requests for warm cache ---");
        Random rng = new Random(42);
        // Zipf distribution: 20% of videos get 80% of traffic
        for (int i = 0; i < 10_000; i++) {
            int videoNum = (rng.nextDouble() < 0.8)
                    ? rng.nextInt(40) + 1      // popular 20%
                    : rng.nextInt(200) + 1;    // long tail
            cache.getVideo("video_" + videoNum);
        }

        cache.getStatistics();

        // Invalidation
        System.out.println();
        cache.invalidate("video_123");
        System.out.println("After invalidation:");
        cache.getVideo("video_123"); // should go to L2/L3 again
    }
}
