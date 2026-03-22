import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

/**
 * Problem 5: Real-Time Analytics Dashboard for Website Traffic
 * Demonstrates: Multiple HashMaps for different dimensions, frequency counting, top-N with PriorityQueue
 */
public class AnalyticsDashboard {

    // ── Data Model ────────────────────────────────────────────────────────────

    public record PageViewEvent(String url, String userId, String source, long timestamp) {}

    // ── Hash Tables ───────────────────────────────────────────────────────────

    // Dimension 1: page → total view count
    private final ConcurrentHashMap<String, AtomicLong> pageViews = new ConcurrentHashMap<>();

    // Dimension 2: page → unique visitor set
    private final ConcurrentHashMap<String, Set<String>> uniqueVisitors = new ConcurrentHashMap<>();

    // Dimension 3: source → count
    private final ConcurrentHashMap<String, AtomicLong> sourceCounts = new ConcurrentHashMap<>();

    // Dimension 4: hour → count (for peak hour detection)
    private final ConcurrentHashMap<Integer, AtomicLong> hourlyTraffic = new ConcurrentHashMap<>();

    private final AtomicLong totalEvents = new AtomicLong(0);

    // Dashboard snapshots (updated every 5 seconds by background thread)
    private volatile DashboardSnapshot latestSnapshot;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AnalyticsDashboard() {
        scheduler.scheduleAtFixedRate(this::buildSnapshot, 0, 5, TimeUnit.SECONDS);
    }

    // ── Event Processing ──────────────────────────────────────────────────────

    /**
     * Process a page view event. O(1) per dimension update.
     * All four hash tables updated atomically in ~constant time.
     */
    public void processEvent(PageViewEvent event) {
        // Dimension 1: page views counter
        pageViews.computeIfAbsent(event.url(), k -> new AtomicLong(0)).incrementAndGet();

        // Dimension 2: unique visitors (ConcurrentHashMap-backed set)
        uniqueVisitors.computeIfAbsent(event.url(),
                k -> ConcurrentHashMap.newKeySet()).add(event.userId());

        // Dimension 3: traffic source
        sourceCounts.computeIfAbsent(event.source(), k -> new AtomicLong(0)).incrementAndGet();

        // Dimension 4: hourly bucket
        int hour = (int) ((event.timestamp() / 3_600_000) % 24);
        hourlyTraffic.computeIfAbsent(hour, k -> new AtomicLong(0)).incrementAndGet();

        totalEvents.incrementAndGet();
    }

    // ── Dashboard Snapshot ────────────────────────────────────────────────────

    private void buildSnapshot() {
        List<PageStats> topPages = getTopN(10);
        Map<String, Long> sources = getSourceBreakdown();
        int peakHour = getPeakHour();
        latestSnapshot = new DashboardSnapshot(topPages, sources, peakHour, totalEvents.get());
    }

    /** Top N most visited pages using a min-heap (PriorityQueue). O(p log n) where p = pages. */
    private List<PageStats> getTopN(int n) {
        // Min-heap of size N for efficient top-N extraction
        PriorityQueue<PageStats> minHeap = new PriorityQueue<>(Comparator.comparingLong(PageStats::views));

        pageViews.forEach((url, count) -> {
            long views = count.get();
            int unique = uniqueVisitors.getOrDefault(url, Set.of()).size();
            PageStats ps = new PageStats(url, views, unique);
            minHeap.offer(ps);
            if (minHeap.size() > n) minHeap.poll(); // evict smallest
        });

        // Return sorted descending
        List<PageStats> result = new ArrayList<>(minHeap);
        result.sort(Comparator.comparingLong(PageStats::views).reversed());
        return result;
    }

    private Map<String, Long> getSourceBreakdown() {
        Map<String, Long> result = new LinkedHashMap<>();
        long total = sourceCounts.values().stream().mapToLong(AtomicLong::get).sum();
        sourceCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicLong>comparingByValue(
                        Comparator.comparingLong(AtomicLong::get)).reversed())
                .forEach(e -> result.put(e.getKey(), e.getValue().get()));
        return result;
    }

    private int getPeakHour() {
        return hourlyTraffic.entrySet().stream()
                .max(Comparator.comparingLong(e -> e.getValue().get()))
                .map(Map.Entry::getKey).orElse(-1);
    }

    // ── Display ───────────────────────────────────────────────────────────────

    public void getDashboard() {
        if (latestSnapshot == null) buildSnapshot();
        DashboardSnapshot snap = latestSnapshot;

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║         Real-Time Analytics Dashboard           ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.printf("  Total Events: %,d%n%n", snap.totalEvents());

        System.out.println("  Top Pages:");
        for (int i = 0; i < snap.topPages().size(); i++) {
            PageStats p = snap.topPages().get(i);
            System.out.printf("    %2d. %-35s %,7d views (%,d unique)%n",
                    i + 1, p.url(), p.views(), p.uniqueVisitors());
        }

        System.out.println("\n  Traffic Sources:");
        long sourceTotal = snap.sources().values().stream().mapToLong(Long::longValue).sum();
        snap.sources().forEach((src, cnt) ->
                System.out.printf("    %-12s %5.1f%%%n", src + ":", sourceTotal == 0 ? 0 : 100.0 * cnt / sourceTotal));

        System.out.printf("%n  Peak Hour: %d:00 - %d:00%n", snap.peakHour(), snap.peakHour() + 1);
    }

    public void shutdown() { scheduler.shutdown(); }

    // ── Records ───────────────────────────────────────────────────────────────

    public record PageStats(String url, long views, int uniqueVisitors) {}
    public record DashboardSnapshot(List<PageStats> topPages, Map<String, Long> sources,
                                    int peakHour, long totalEvents) {}

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        AnalyticsDashboard dashboard = new AnalyticsDashboard();

        System.out.println("=== Real-Time Analytics Dashboard ===\n");
        System.out.println("Simulating 100,000 page view events...");

        String[] pages   = {"/article/breaking-news", "/sports/championship", "/tech/ai-update",
                            "/world/politics", "/entertainment/movies"};
        String[] sources = {"Google", "Facebook", "Direct", "Twitter", "Other"};
        Random rng = new Random(42);

        // Simulate events with realistic distribution (Zipf-like for pages)
        int[] pageWeights = {40, 25, 15, 12, 8}; // percent distribution
        for (int i = 0; i < 100_000; i++) {
            int r = rng.nextInt(100);
            int pageIdx = 0, cum = 0;
            for (int j = 0; j < pageWeights.length; j++) {
                cum += pageWeights[j];
                if (r < cum) { pageIdx = j; break; }
            }
            dashboard.processEvent(new PageViewEvent(
                    pages[pageIdx],
                    "user_" + rng.nextInt(50_000),
                    sources[rng.nextInt(sources.length)],
                    System.currentTimeMillis() - rng.nextLong(86_400_000)
            ));
        }

        dashboard.getDashboard();
        dashboard.shutdown();
    }
}
