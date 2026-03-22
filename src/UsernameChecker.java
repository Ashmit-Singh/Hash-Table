import java.util.*;

/**
 * Problem 1: Social Media Username Availability Checker
 * Demonstrates: Hash table basics, O(1) lookup, frequency counting, collision handling
 */
public class UsernameChecker {

    // username -> userId mapping (registered users)
    private final HashMap<String, Integer> registeredUsers = new HashMap<>();

    // username -> attempt count (tracks popularity)
    private final HashMap<String, Integer> attemptFrequency = new HashMap<>();

    private int nextUserId = 1;

    // ── Core Operations ────────────────────────────────────────────────────────

    /** Register a new username. Returns false if already taken. */
    public boolean register(String username) {
        if (registeredUsers.containsKey(username)) return false;
        registeredUsers.put(username, nextUserId++);
        return true;
    }

    /**
     * Check availability in O(1) time.
     * Also tracks attempt frequency for analytics.
     */
    public boolean checkAvailability(String username) {
        // Track every check attempt
        attemptFrequency.merge(username, 1, Integer::sum);
        return !registeredUsers.containsKey(username);
    }

    /**
     * Suggest alternative usernames when the requested one is taken.
     * Strategies: append numbers, replace underscore with dot, add prefix/suffix
     */
    public List<String> suggestAlternatives(String username) {
        List<String> suggestions = new ArrayList<>();

        // Strategy 1: Append numbers 1-5
        for (int i = 1; i <= 5; i++) {
            String candidate = username + i;
            if (!registeredUsers.containsKey(candidate)) {
                suggestions.add(candidate);
                if (suggestions.size() >= 3) break;
            }
        }

        // Strategy 2: Replace _ with .
        String dotVariant = username.replace('_', '.');
        if (!registeredUsers.containsKey(dotVariant) && !suggestions.contains(dotVariant)) {
            suggestions.add(dotVariant);
        }

        // Strategy 3: Add common suffixes
        for (String suffix : new String[]{"_real", "_official", "_pro"}) {
            String candidate = username + suffix;
            if (!registeredUsers.containsKey(candidate) && suggestions.size() < 5) {
                suggestions.add(candidate);
            }
        }

        return suggestions;
    }

    /**
     * Get the most attempted (most popular) username.
     * O(n) scan — could be optimized with a max-heap for real-time top-K.
     */
    public Map.Entry<String, Integer> getMostAttempted() {
        return attemptFrequency.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
    }

    /** Return top N most attempted usernames. */
    public List<Map.Entry<String, Integer>> getTopAttempted(int n) {
        return attemptFrequency.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .toList();
    }

    public int totalRegistered() { return registeredUsers.size(); }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        UsernameChecker checker = new UsernameChecker();

        // Seed some registered users
        checker.register("john_doe");
        checker.register("jane_smith");
        checker.register("admin");
        checker.register("john_doe1");
        checker.register("john_doe2");

        System.out.println("=== Username Availability Checker ===\n");

        // Check availability
        System.out.println("checkAvailability(\"john_doe\")   → " + checker.checkAvailability("john_doe")
                + " (taken)");
        System.out.println("checkAvailability(\"jane_smith\") → " + checker.checkAvailability("jane_smith")
                + " (taken)");
        System.out.println("checkAvailability(\"newuser99\")  → " + checker.checkAvailability("newuser99")
                + " (available)");

        // Simulate many attempts for "admin"
        for (int i = 0; i < 10_543; i++) checker.checkAvailability("admin");

        System.out.println("\nsuggestAlternatives(\"john_doe\") → " + checker.suggestAlternatives("john_doe"));

        Map.Entry<String, Integer> top = checker.getMostAttempted();
        System.out.println("\ngetMostAttempted() → \"" + top.getKey() + "\" (" + top.getValue() + " attempts)");

        System.out.println("\nTop 3 attempted:");
        checker.getTopAttempted(3).forEach(e ->
                System.out.printf("  %-15s %,d attempts%n", e.getKey(), e.getValue()));

        System.out.println("\nTotal registered users: " + checker.totalRegistered());
        System.out.println("Hash table load factor note: Java HashMap default load factor = 0.75");
    }
}
