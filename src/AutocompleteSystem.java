import java.util.*;
import java.util.stream.*;

/**
 * Problem 7: Autocomplete System for Search Engine
 * Demonstrates: HashMap for frequency, Trie + HashMap hybrid for prefix matching, min-heap for top-K
 *
 * Architecture:
 *   - Trie node stores a Set<String> of queries sharing that prefix.
 *   - Global HashMap<query, frequency> stores exact frequencies.
 *   - For each prefix lookup: traverse Trie to get candidate queries, then
 *     rank by frequency from the HashMap using a min-heap.
 */
public class AutocompleteSystem {

    // ── Trie ──────────────────────────────────────────────────────────────────

    private static class TrieNode {
        final Map<Character, TrieNode> children = new HashMap<>();
        final Set<String> queries = new HashSet<>(); // all queries with this prefix
        boolean isEndOfWord = false;
    }

    private final TrieNode root = new TrieNode();

    // ── Frequency Table ───────────────────────────────────────────────────────

    private final HashMap<String, Long> frequency = new HashMap<>();

    // ── Indexing ──────────────────────────────────────────────────────────────

    /**
     * Add or update a query with its frequency.
     * O(L) where L = query length.
     */
    public void addQuery(String query, long count) {
        query = normalize(query);
        frequency.merge(query, count, Long::sum);
        insertTrie(query);
    }

    private void insertTrie(String query) {
        TrieNode node = root;
        for (char c : query.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
            node.queries.add(query);
        }
        node.isEndOfWord = true;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Return top K suggestions for a prefix.
     * O(L + P log K) where P = matching queries, K = result count.
     */
    public List<Suggestion> search(String prefix, int topK) {
        prefix = normalize(prefix);
        TrieNode node = traverseTrie(prefix);
        if (node == null) return List.of(); // prefix not found

        Set<String> candidates = node.queries;

        // Min-heap of size K for efficient top-K extraction
        PriorityQueue<Suggestion> minHeap = new PriorityQueue<>(Comparator.comparingLong(Suggestion::count));

        for (String q : candidates) {
            long cnt = frequency.getOrDefault(q, 0L);
            minHeap.offer(new Suggestion(q, cnt));
            if (minHeap.size() > topK) minHeap.poll();
        }

        List<Suggestion> result = new ArrayList<>(minHeap);
        result.sort(Comparator.comparingLong(Suggestion::count).reversed());
        return result;
    }

    /**
     * Increment frequency of a query after a user runs it.
     * O(L) — just update the HashMap; Trie structure unchanged.
     */
    public void updateFrequency(String query) {
        query = normalize(query);
        long before = frequency.getOrDefault(query, 0L);
        frequency.merge(query, 1L, Long::sum);
        long after = frequency.get(query);
        if (!traverseTrie(query.substring(0, 1)) .queries.contains(query)) insertTrie(query);
        System.out.printf("updateFrequency(\"%s\") → Frequency: %d → %d%s%n",
                query, before, after, after > before + 1 ? "" : " (trending)");
    }

    /**
     * Simple typo correction: find the closest query in the index
     * using edit distance (Levenshtein). O(P × L²) — only for small result sets.
     */
    public List<Suggestion> correctTypo(String query, int topK) {
        query = normalize(query);
        String finalQuery = query;
        return frequency.entrySet().stream()
                .filter(e -> Math.abs(e.getKey().length() - finalQuery.length()) <= 3)
                .map(e -> new Suggestion(e.getKey(), e.getValue(),
                        levenshtein(finalQuery, e.getKey())))
                .filter(s -> s.editDistance() <= 2)
                .sorted(Comparator.comparingInt(Suggestion::editDistance)
                        .thenComparing(Comparator.comparingLong(Suggestion::count).reversed()))
                .limit(topK)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TrieNode traverseTrie(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            node = node.children.get(c);
            if (node == null) return null;
        }
        return node;
    }

    private String normalize(String s) { return s.toLowerCase().trim(); }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
        return dp[a.length()][b.length()];
    }

    public int totalQueries() { return frequency.size(); }

    // ── Records ───────────────────────────────────────────────────────────────

    public record Suggestion(String query, long count, int editDistance) {
        Suggestion(String query, long count) { this(query, count, 0); }
    }

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        AutocompleteSystem ac = new AutocompleteSystem();

        System.out.println("=== Autocomplete System ===\n");

        // Seed with popular queries (simulating 10M history)
        ac.addQuery("java tutorial", 1_234_567);
        ac.addQuery("javascript", 987_654);
        ac.addQuery("java download", 456_789);
        ac.addQuery("java 21 features", 1);
        ac.addQuery("java spring boot", 234_000);
        ac.addQuery("java vs python", 189_000);
        ac.addQuery("javascript frameworks", 312_000);
        ac.addQuery("javascript tutorial", 654_321);
        ac.addQuery("python tutorial", 1_100_000);
        ac.addQuery("python download", 400_000);
        ac.addQuery("python vs java", 175_000);

        System.out.printf("Index built: %,d queries%n%n", ac.totalQueries());

        // Prefix search
        System.out.println("search(\"jav\") →");
        ac.search("jav", 5).forEach(s ->
                System.out.printf("  %2d. %-30s (%,d searches)%n",
                        ac.search("jav", 5).indexOf(s) + 1, s.query(), s.count()));

        System.out.println("\nsearch(\"java \") →");
        ac.search("java ", 5).forEach(s ->
                System.out.printf("  %-30s (%,d searches)%n", s.query(), s.count()));

        // Update frequency (simulate user searching a trending term)
        System.out.println();
        ac.updateFrequency("java 21 features");
        ac.updateFrequency("java 21 features");

        // Typo correction
        System.out.println("\ncorrectTypo(\"javscript\") →");
        ac.correctTypo("javscript", 3).forEach(s ->
                System.out.printf("  \"%s\" (edit distance: %d, count: %,d)%n",
                        s.query(), s.editDistance(), s.count()));

        System.out.println("\n--- Complexity ---");
        System.out.println("Insert: O(L)  |  Prefix search: O(L + P log K)  |  Freq update: O(1)");
    }
}
