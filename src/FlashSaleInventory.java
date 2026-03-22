import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Problem 2: E-commerce Flash Sale Inventory Manager
 * Demonstrates: Hash table stock lookup, thread-safe atomic ops, LinkedHashMap waiting list (FIFO)
 */
public class FlashSaleInventory {

    // productId -> stock count (atomic for thread safety)
    private final ConcurrentHashMap<String, AtomicInteger> stock = new ConcurrentHashMap<>();

    // productId -> FIFO waiting list of userIds
    private final ConcurrentHashMap<String, LinkedList<Integer>> waitingList = new ConcurrentHashMap<>();

    // productId -> purchase log
    private final ConcurrentHashMap<String, List<String>> purchaseLog = new ConcurrentHashMap<>();

    // ── Setup ─────────────────────────────────────────────────────────────────

    public void addProduct(String productId, int quantity) {
        stock.put(productId, new AtomicInteger(quantity));
        waitingList.put(productId, new LinkedList<>());
        purchaseLog.put(productId, Collections.synchronizedList(new ArrayList<>()));
    }

    // ── Core Operations ────────────────────────────────────────────────────────

    /** O(1) stock check. */
    public int checkStock(String productId) {
        AtomicInteger s = stock.get(productId);
        return s == null ? -1 : s.get();
    }

    /**
     * Thread-safe purchase using compareAndSet loop.
     * Returns PurchaseResult indicating success or waitlist position.
     */
    public PurchaseResult purchaseItem(String productId, int userId) {
        AtomicInteger s = stock.get(productId);
        if (s == null) return new PurchaseResult(false, -1, "Product not found");

        // Spin-CAS: decrement only if > 0
        while (true) {
            int current = s.get();
            if (current <= 0) {
                // Out of stock → add to waiting list
                LinkedList<Integer> wl = waitingList.get(productId);
                synchronized (wl) {
                    wl.addLast(userId);
                    int position = wl.size();
                    return new PurchaseResult(false, position,
                            "Out of stock. Added to waiting list at position #" + position);
                }
            }
            if (s.compareAndSet(current, current - 1)) {
                purchaseLog.get(productId).add("User " + userId + " purchased. Remaining: " + (current - 1));
                return new PurchaseResult(true, current - 1, "Purchase successful! " + (current - 1) + " units remaining");
            }
            // Another thread won the CAS, retry
        }
    }

    /** Notify the next person on the waiting list (e.g., when a return happens). */
    public Integer notifyNextOnWaitlist(String productId) {
        LinkedList<Integer> wl = waitingList.get(productId);
        if (wl == null) return null;
        synchronized (wl) {
            return wl.isEmpty() ? null : wl.removeFirst();
        }
    }

    public int waitlistSize(String productId) {
        LinkedList<Integer> wl = waitingList.get(productId);
        return wl == null ? 0 : wl.size();
    }

    // ── Result Record ─────────────────────────────────────────────────────────

    public record PurchaseResult(boolean success, int value, String message) {}

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        FlashSaleInventory inventory = new FlashSaleInventory();
        inventory.addProduct("IPHONE15_256GB", 100);

        System.out.println("=== Flash Sale Inventory Manager ===\n");
        System.out.println("checkStock(\"IPHONE15_256GB\") → " + inventory.checkStock("IPHONE15_256GB") + " units available");

        // Simulate 50,000 concurrent buyers with thread pool
        int CONCURRENT_BUYERS = 50_000;
        ExecutorService pool = Executors.newFixedThreadPool(200);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_BUYERS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger waitlistCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_BUYERS; i++) {
            final int userId = i + 1;
            pool.submit(() -> {
                PurchaseResult result = inventory.purchaseItem("IPHONE15_256GB", userId);
                if (result.success()) successCount.incrementAndGet();
                else waitlistCount.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await();
        pool.shutdown();

        System.out.println("\nSimulation complete (" + CONCURRENT_BUYERS + " concurrent buyers):");
        System.out.println("  Successful purchases : " + successCount.get());
        System.out.println("  Added to waitlist    : " + waitlistCount.get());
        System.out.println("  Remaining stock      : " + inventory.checkStock("IPHONE15_256GB"));
        System.out.println("  Waitlist size        : " + inventory.waitlistSize("IPHONE15_256GB"));

        // Show first few log entries
        System.out.println("\nFirst 3 purchase log entries:");
        inventory.purchaseLog.get("IPHONE15_256GB").stream().limit(3)
                .forEach(e -> System.out.println("  " + e));

        System.out.println("\nNext on waitlist: User #" + inventory.notifyNextOnWaitlist("IPHONE15_256GB"));
    }
}
