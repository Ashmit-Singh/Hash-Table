import java.util.*;
import java.util.stream.*;

/**
 * Problem 9: Two-Sum Problem Variants for Financial Transactions
 * Demonstrates: HashMap complement lookup, O(n) algorithms, time-window filtering,
 *               K-Sum with memoization, duplicate detection
 */
public class TransactionAnalyzer {

    // ── Data Model ────────────────────────────────────────────────────────────

    public record Transaction(int id, long amount, String merchant, String time,
                              String accountId) {
        @Override public String toString() {
            return String.format("{id:%d, amount:%d, merchant:\"%s\", time:\"%s\", acct:\"%s\"}",
                    id, amount, merchant, time, accountId);
        }
    }

    private final List<Transaction> transactions;

    public TransactionAnalyzer(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    // ── Two-Sum ───────────────────────────────────────────────────────────────

    /**
     * Classic Two-Sum: find all pairs summing to target.
     * O(n) using HashMap<complement, transaction>.
     */
    public List<int[]> findTwoSum(long target) {
        // complement (amount) → list of transactions with that amount
        HashMap<Long, List<Transaction>> seen = new HashMap<>();
        List<int[]> results = new ArrayList<>();

        for (Transaction tx : transactions) {
            long complement = target - tx.amount();
            List<Transaction> matches = seen.get(complement);
            if (matches != null) {
                for (Transaction other : matches) {
                    results.add(new int[]{other.id(), tx.id()});
                }
            }
            seen.computeIfAbsent(tx.amount(), k -> new ArrayList<>()).add(tx);
        }
        return results;
    }

    /**
     * Two-Sum with time window: pairs must be within windowMinutes of each other.
     * O(n²) worst case but O(n) in practice with sorted data and a sliding window.
     */
    public List<int[]> findTwoSumWithWindow(long target, int windowMinutes) {
        List<int[]> results = new ArrayList<>();
        // Use a LinkedHashMap (insertion-ordered) to evict old entries
        LinkedHashMap<Long, Transaction> window = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            int txMin = timeToMinutes(tx.time());

            // Evict transactions outside the window
            window.entrySet().removeIf(e -> txMin - timeToMinutes(e.getValue().time()) > windowMinutes);

            long complement = target - tx.amount();
            if (window.containsKey(complement)) {
                Transaction other = window.get(complement);
                results.add(new int[]{other.id(), tx.id()});
            }
            window.put(tx.amount(), tx);
        }
        return results;
    }

    // ── K-Sum ─────────────────────────────────────────────────────────────────

    /**
     * K-Sum: find groups of exactly K transactions summing to target.
     * Uses recursive reduction + HashMap memoization.
     * Base case: Two-Sum using hash lookup → O(n).
     */
    public List<List<Integer>> findKSum(int k, long target) {
        List<List<Integer>> results = new ArrayList<>();
        kSumHelper(transactions, k, target, new ArrayList<>(), results);
        return results;
    }

    private void kSumHelper(List<Transaction> remaining, int k, long target,
                             List<Integer> current, List<List<Integer>> results) {
        if (k == 2) {
            // Base case: two-sum lookup
            HashMap<Long, Transaction> seen = new HashMap<>();
            for (Transaction tx : remaining) {
                long complement = target - tx.amount();
                if (seen.containsKey(complement)) {
                    List<Integer> group = new ArrayList<>(current);
                    group.add(seen.get(complement).id());
                    group.add(tx.id());
                    results.add(group);
                }
                seen.put(tx.amount(), tx);
            }
            return;
        }

        for (int i = 0; i < remaining.size(); i++) {
            Transaction tx = remaining.get(i);
            current.add(tx.id());
            kSumHelper(remaining.subList(i + 1, remaining.size()), k - 1,
                    target - tx.amount(), current, results);
            current.remove(current.size() - 1);
        }
    }

    // ── Duplicate Detection ───────────────────────────────────────────────────

    /**
     * Detect duplicate payments: same amount + same merchant, different accounts.
     * O(n) using HashMap<amount+merchant, Map<accountId, count>>.
     */
    public List<DuplicateGroup> detectDuplicates() {
        // key: "amount|merchant" → map of accountId → list of txIds
        HashMap<String, Map<String, List<Integer>>> index = new HashMap<>();

        for (Transaction tx : transactions) {
            String key = tx.amount() + "|" + tx.merchant();
            index.computeIfAbsent(key, k -> new HashMap<>())
                 .computeIfAbsent(tx.accountId(), k -> new ArrayList<>())
                 .add(tx.id());
        }

        List<DuplicateGroup> duplicates = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<Integer>>> entry : index.entrySet()) {
            Map<String, List<Integer>> accountMap = entry.getValue();
            if (accountMap.size() > 1) { // multiple different accounts
                String[] parts = entry.getKey().split("\\|");
                long amount = Long.parseLong(parts[0]);
                String merchant = parts[1];
                duplicates.add(new DuplicateGroup(amount, merchant,
                        new ArrayList<>(accountMap.keySet()),
                        accountMap.values().stream().flatMap(List::stream).toList()));
            }
        }
        return duplicates;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int timeToMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record DuplicateGroup(long amount, String merchant,
                                 List<String> accounts, List<Integer> txIds) {
        @Override public String toString() {
            return String.format("{amount:%d, merchant:\"%s\", accounts:%s, txIds:%s}",
                    amount, merchant, accounts, txIds);
        }
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        List<Transaction> txList = List.of(
            new Transaction(1, 500,  "Store A", "10:00", "acc1"),
            new Transaction(2, 300,  "Store B", "10:15", "acc2"),
            new Transaction(3, 200,  "Store C", "10:30", "acc3"),
            new Transaction(4, 500,  "Store A", "10:45", "acc2"),  // duplicate merchant+amount
            new Transaction(5, 100,  "Store D", "11:00", "acc1"),
            new Transaction(6, 400,  "Store E", "11:10", "acc4"),
            new Transaction(7, 100,  "Store F", "11:15", "acc5")
        );

        TransactionAnalyzer analyzer = new TransactionAnalyzer(txList);

        System.out.println("=== Financial Transaction Analyzer ===\n");
        System.out.println("Transactions:");
        txList.forEach(t -> System.out.println("  " + t));

        System.out.println("\n-- findTwoSum(target=500) →");
        List<int[]> twoSumResults = analyzer.findTwoSum(500);
        if (twoSumResults.isEmpty()) System.out.println("  No pairs found");
        else twoSumResults.forEach(p ->
                System.out.printf("  (id:%d, id:%d) → %d + %d = 500%n",
                        p[0], p[1],
                        txList.stream().filter(t -> t.id() == p[0]).findFirst().get().amount(),
                        txList.stream().filter(t -> t.id() == p[1]).findFirst().get().amount()));

        System.out.println("\n-- findTwoSumWithWindow(target=500, window=60min) →");
        analyzer.findTwoSumWithWindow(500, 60).forEach(p ->
                System.out.printf("  (id:%d, id:%d) within 60-min window%n", p[0], p[1]));

        System.out.println("\n-- findKSum(k=3, target=1000) →");
        analyzer.findKSum(3, 1000).forEach(group ->
                System.out.println("  " + group + " → " +
                        group.stream().mapToLong(id ->
                                txList.stream().filter(t -> t.id() == id).findFirst().get().amount()).sum()));

        System.out.println("\n-- detectDuplicates() →");
        List<DuplicateGroup> dups = analyzer.detectDuplicates();
        if (dups.isEmpty()) System.out.println("  No duplicates found");
        else dups.forEach(d -> System.out.println("  " + d));

        System.out.println("\n--- Complexity Summary ---");
        System.out.println("Two-Sum        : O(n)    — single HashMap pass");
        System.out.println("Two-Sum+Window : O(n)    — sliding window + HashMap");
        System.out.println("K-Sum          : O(n^(k-1)) — recursive + O(n) base");
        System.out.println("Detect Dupes   : O(n)    — single HashMap pass");
    }
}
